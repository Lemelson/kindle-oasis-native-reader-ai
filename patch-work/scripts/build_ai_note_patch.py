#!/usr/bin/env python3
import shutil
import struct
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ORIGINAL_JAR = ROOT / "from-kindle/native-reader-research/jars/ReaderSDK-impl.jar"
ORIGINAL_READER_JAR = ROOT / "from-kindle/native-reader-research/jars/Reader-plugin.jar"
CLASSES_DIR = ROOT / "patch-work/classes-ai-v27"
OUT_DIR = ROOT / "patch-work/patched-ai-v27"
OUT_JAR = OUT_DIR / "ReaderSDK-impl.ai-v27.jar"
OUT_READER_JAR = OUT_DIR / "Reader-plugin.v27.jar"
GENERATED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)

ADD_NOTE_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/notesandmarks/"
    "AnnotationsSelectionDialogProvider$AddNoteAction"
)
NOTE_ACTION_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/notesandmarks/"
    "AnnotationsSelectionDialogProvider$NoteAction"
)
PROVIDER_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/notesandmarks/"
    "AnnotationsSelectionDialogProvider"
)
AI_CONTROLLER_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/notesandmarks/AiAssistantController"
)
AUTO_UNDO_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/notesandmarks/AutoUndoSelectionSession"
)
CONTENT_PANEL_MOUSE_CLASS = "com/amazon/ebook/booklet/reader/impl/ContentPanel$d"
SELECTION_DIALOG_CLASS = "com/amazon/ebook/booklet/reader/sdk/ui/SelectionDialog"
BOOK_TAP_METHOD = "mouseClicked"
BOOK_TAP_METHOD_DESC = "(Ljava/awt/event/MouseEvent;)V"
BOOK_TAP_EXPECTED_OFFSETS = (281,)
SHARE_ACTION_CLASS = (
    "com/amazon/ebook/booklet/reader/plugin/share/ShareSelectedTextAction"
)


def u1(value):
    return struct.pack(">B", value)


def u2(value):
    return struct.pack(">H", value)


def u4(value):
    return struct.pack(">I", value)


def read_u2(data, pos):
    return struct.unpack(">H", data[pos:pos + 2])[0], pos + 2


def read_u4(data, pos):
    return struct.unpack(">I", data[pos:pos + 4])[0], pos + 4


class ConstantPool:
    def __init__(self, data):
        self.data = data
        self.entries = [None]
        self.cp_count = struct.unpack(">H", data[8:10])[0]
        self.cp_start = 10
        self.cp_end = self._parse()

    def _parse(self):
        pos = self.cp_start
        index = 1
        while index < self.cp_count:
            start = pos
            tag = self.data[pos]
            pos += 1
            info = {"tag": tag}
            if tag == 1:
                length, pos = read_u2(self.data, pos)
                raw_bytes = self.data[pos:pos + length]
                pos += length
                info["value"] = raw_bytes.decode("utf-8")
            elif tag in (3, 4):
                pos += 4
            elif tag in (5, 6):
                pos += 8
                info["raw"] = self.data[start:pos]
                self.entries.append(info)
                self.entries.append(None)
                index += 2
                continue
            elif tag in (7, 8, 16):
                ref, pos = read_u2(self.data, pos)
                info["refs"] = (ref,)
            elif tag in (9, 10, 11, 12, 18):
                ref1, pos = read_u2(self.data, pos)
                ref2, pos = read_u2(self.data, pos)
                info["refs"] = (ref1, ref2)
            elif tag == 15:
                kind = self.data[pos]
                ref, pos = read_u2(self.data, pos + 1)
                info["refs"] = (kind, ref)
            else:
                raise ValueError(f"unsupported constant pool tag {tag} at index {index}")
            info["raw"] = self.data[start:pos]
            self.entries.append(info)
            index += 1
        return pos

    def utf8(self, index):
        entry = self.entries[index]
        if entry is None or entry["tag"] != 1:
            raise ValueError(f"constant #{index} is not Utf8")
        return entry["value"]

    def class_name(self, index):
        entry = self.entries[index]
        if entry is None or entry["tag"] != 7:
            raise ValueError(f"constant #{index} is not Class")
        return self.utf8(entry["refs"][0])

    def name_and_type(self, index):
        entry = self.entries[index]
        if entry is None or entry["tag"] != 12:
            raise ValueError(f"constant #{index} is not NameAndType")
        return self.utf8(entry["refs"][0]), self.utf8(entry["refs"][1])

    def add_utf8(self, value):
        for index, entry in enumerate(self.entries):
            if entry is not None and entry["tag"] == 1 and entry.get("value") == value:
                return index
        encoded = value.encode("utf-8")
        raw = u1(1) + u2(len(encoded)) + encoded
        return self._append({"tag": 1, "value": value, "raw": raw})

    def add_class(self, internal_name):
        name_index = self.add_utf8(internal_name)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry["tag"] == 7 and entry.get("refs") == (name_index,):
                return index
        return self._append({"tag": 7, "refs": (name_index,), "raw": u1(7) + u2(name_index)})

    def add_string(self, value):
        utf8_index = self.add_utf8(value)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry["tag"] == 8 and entry.get("refs") == (utf8_index,):
                return index
        return self._append({"tag": 8, "refs": (utf8_index,), "raw": u1(8) + u2(utf8_index)})

    def add_name_and_type(self, name, desc):
        name_index = self.add_utf8(name)
        desc_index = self.add_utf8(desc)
        refs = (name_index, desc_index)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry["tag"] == 12 and entry.get("refs") == refs:
                return index
        return self._append({"tag": 12, "refs": refs, "raw": u1(12) + u2(name_index) + u2(desc_index)})

    def add_methodref(self, class_name, method_name, desc):
        class_index = self.add_class(class_name)
        nt_index = self.add_name_and_type(method_name, desc)
        refs = (class_index, nt_index)
        for index, entry in enumerate(self.entries):
            if entry is not None and entry["tag"] == 10 and entry.get("refs") == refs:
                return index
        return self._append({"tag": 10, "refs": refs, "raw": u1(10) + u2(class_index) + u2(nt_index)})

    def find_fieldref(self, class_name, field_name, desc):
        for index, entry in enumerate(self.entries):
            if entry is None or entry["tag"] != 9:
                continue
            cls = self.class_name(entry["refs"][0])
            name, field_desc = self.name_and_type(entry["refs"][1])
            if cls == class_name and name == field_name and field_desc == desc:
                return index
        raise ValueError(f"fieldref not found: {class_name}.{field_name}:{desc}")

    def find_methodref(self, class_name, method_name, desc):
        for index, entry in enumerate(self.entries):
            if entry is None or entry["tag"] != 10:
                continue
            cls = self.class_name(entry["refs"][0])
            name, method_desc = self.name_and_type(entry["refs"][1])
            if cls == class_name and name == method_name and method_desc == desc:
                return index
        raise ValueError(f"methodref not found: {class_name}.{method_name}{desc}")

    def find_interface_methodref(self, class_name, method_name, desc):
        for index, entry in enumerate(self.entries):
            if entry is None or entry["tag"] != 11:
                continue
            cls = self.class_name(entry["refs"][0])
            name, method_desc = self.name_and_type(entry["refs"][1])
            if cls == class_name and name == method_name and method_desc == desc:
                return index
        raise ValueError(
            f"interface methodref not found: {class_name}.{method_name}{desc}"
        )

    def _append(self, entry):
        self.entries.append(entry)
        return len(self.entries) - 1

    def bytes(self):
        out = bytearray()
        out.extend(u2(len(self.entries)))
        for entry in self.entries[1:]:
            if entry is not None:
                out.extend(entry["raw"])
        return bytes(out)


def ldc(index):
    if index <= 0xFF:
        return b"\x12" + u1(index)
    return b"\x13" + u2(index)


def build_code_attribute(code_name_index, code, max_stack, max_locals):
    body = bytearray()
    body.extend(u2(max_stack))
    body.extend(u2(max_locals))
    body.extend(u4(len(code)))
    body.extend(code)
    body.extend(u2(0))
    body.extend(u2(0))
    return u2(code_name_index) + u4(len(body)) + body


def method_info(access_flags, name_index, desc_index, code_name_index, code, max_stack, max_locals):
    out = bytearray()
    out.extend(u2(access_flags))
    out.extend(u2(name_index))
    out.extend(u2(desc_index))
    out.extend(u2(1))
    out.extend(build_code_attribute(code_name_index, code, max_stack, max_locals))
    return bytes(out)


def skip_attributes(data, pos):
    count, pos = read_u2(data, pos)
    for _ in range(count):
        pos += 2
        length, pos = read_u4(data, pos)
        pos += length
    return pos


def method_code_range(data, cp, method_name, method_desc):
    pos = cp.cp_end
    pos += 6  # access_flags, this_class, super_class
    interfaces_count, pos = read_u2(data, pos)
    pos += interfaces_count * 2

    fields_count, pos = read_u2(data, pos)
    for _ in range(fields_count):
        pos += 6
        pos = skip_attributes(data, pos)

    methods_count, pos = read_u2(data, pos)
    matches = []
    for _ in range(methods_count):
        pos += 2  # access_flags
        name_index, pos = read_u2(data, pos)
        desc_index, pos = read_u2(data, pos)
        attributes_count, pos = read_u2(data, pos)
        name = cp.utf8(name_index)
        desc = cp.utf8(desc_index)
        for _attr in range(attributes_count):
            attr_name_index, pos = read_u2(data, pos)
            attr_length, pos = read_u4(data, pos)
            attr_body_start = pos
            if (name == method_name
                    and desc == method_desc
                    and cp.utf8(attr_name_index) == "Code"):
                code_length = struct.unpack(
                    ">I", data[attr_body_start + 4:attr_body_start + 8]
                )[0]
                code_start = attr_body_start + 8
                matches.append((code_start, code_start + code_length))
            pos += attr_length

    if len(matches) != 1:
        raise ValueError(
            f"expected exactly 1 {method_name}{method_desc} Code attribute, "
            f"found {len(matches)}"
        )
    return matches[0]


def content_panel_book_tap_pattern(data, cp):
    selection_state_field = cp.find_fieldref(
        CONTENT_PANEL_MOUSE_CLASS, "aOa", "Z"
    )
    native_close = cp.find_interface_methodref(
        SELECTION_DIALOG_CLASS, "bQ", "(Z)V"
    )
    return (
        b"\x19\x04"                       # aload 4 (SelectionDialog)
        + b"\x2a"                         # aload_0
        + b"\xb4" + u2(selection_state_field)  # getfield aOa:Z
        + b"\xb9" + u2(native_close) + b"\x02\x00"  # invokeinterface bQ
    )


def content_panel_book_tap_replacement(cp):
    selection_state_field = cp.find_fieldref(
        CONTENT_PANEL_MOUSE_CLASS, "aOa", "Z"
    )
    hook = cp.add_methodref(
        AUTO_UNDO_CLASS,
        "handleBookTap",
        f"(L{SELECTION_DIALOG_CLASS};Z)V",
    )
    return (
        b"\x19\x04"                       # aload 4 (SelectionDialog)
        + b"\x2a"                         # aload_0
        + b"\xb4" + u2(selection_state_field)  # getfield aOa:Z
        + b"\xb8" + u2(hook)              # invokestatic handleBookTap
        + b"\x00\x00"                     # preserve original bytecode length
    )


def find_pattern_offsets(code, pattern):
    offsets = []
    start = 0
    while True:
        offset = code.find(pattern, start)
        if offset < 0:
            return offsets
        offsets.append(offset)
        start = offset + len(pattern)


def patch_content_panel_book_tap_class(original):
    cp = ConstantPool(original)
    code_start, code_end = method_code_range(
        original, cp, BOOK_TAP_METHOD, BOOK_TAP_METHOD_DESC
    )
    original_code = original[code_start:code_end]
    pattern = content_panel_book_tap_pattern(original, cp)
    offsets = find_pattern_offsets(original_code, pattern)
    if tuple(offsets) != BOOK_TAP_EXPECTED_OFFSETS:
        raise ValueError(
            "expected exactly 1 native book-tap close call at offsets "
            f"{BOOK_TAP_EXPECTED_OFFSETS}, found {tuple(offsets)}"
        )

    replacement = content_panel_book_tap_replacement(cp)
    if len(replacement) != len(pattern):
        raise ValueError("book-tap hook must preserve bytecode length")

    patched_code = bytearray(original_code)
    for offset in offsets:
        patched_code[offset:offset + len(pattern)] = replacement

    body = bytearray(original[cp.cp_end:])
    relative_start = code_start - cp.cp_end
    relative_end = code_end - cp.cp_end
    body[relative_start:relative_end] = patched_code

    patched = bytearray()
    patched.extend(original[:8])
    patched.extend(cp.bytes())
    patched.extend(body)
    return bytes(patched)


def count_content_panel_book_tap_hooks(data):
    cp = ConstantPool(data)
    code_start, code_end = method_code_range(
        data, cp, BOOK_TAP_METHOD, BOOK_TAP_METHOD_DESC
    )
    replacement = content_panel_book_tap_replacement(cp)
    return len(find_pattern_offsets(data[code_start:code_end], replacement))


def patch_add_note_class(original):
    cp = ConstantPool(original)
    provider_desc = f"L{PROVIDER_CLASS};"
    constructor_desc = f"({provider_desc})V"
    note_constructor_desc = f"({provider_desc}Ljava/lang/String;)V"
    action_desc = "(Ljava/awt/event/ActionEvent;)V"

    code_name = cp.add_utf8("Code")
    ai_string = cp.add_string("AI")
    c_ut_field = cp.find_fieldref(ADD_NOTE_CLASS, "cUT", provider_desc)
    note_constructor = cp.find_methodref(NOTE_ACTION_CLASS, "<init>", note_constructor_desc)
    ai_open = cp.add_methodref(AI_CONTROLLER_CLASS, "open", constructor_desc)

    constructor_code = bytearray()
    constructor_code.extend(b"\x2a")             # aload_0
    constructor_code.extend(b"\x2b")             # aload_1
    constructor_code.extend(b"\xb5" + u2(c_ut_field))
    constructor_code.extend(b"\x2a")             # aload_0
    constructor_code.extend(b"\x2b")             # aload_1
    constructor_code.extend(ldc(ai_string))
    constructor_code.extend(b"\xb7" + u2(note_constructor))
    constructor_code.extend(b"\xb1")             # return

    action_code = bytearray()
    action_code.extend(b"\x2a")                  # aload_0
    action_code.extend(b"\xb4" + u2(c_ut_field))
    action_code.extend(b"\xb8" + u2(ai_open))
    action_code.extend(b"\xb1")                  # return

    pos = cp.cp_end
    class_body_prefix_start = pos
    pos += 6  # access_flags, this_class, super_class
    interfaces_count, pos = read_u2(original, pos)
    pos += interfaces_count * 2

    fields_count, pos = read_u2(original, pos)
    for _ in range(fields_count):
        pos += 6
        pos = skip_attributes(original, pos)

    methods_count_pos = pos
    methods_count, pos = read_u2(original, pos)
    methods = []
    for _ in range(methods_count):
        method_start = pos
        access_flags, pos = read_u2(original, pos)
        name_index, pos = read_u2(original, pos)
        desc_index, pos = read_u2(original, pos)
        attributes_count, pos = read_u2(original, pos)
        for _attr in range(attributes_count):
            pos += 2
            length, pos = read_u4(original, pos)
            pos += length
        method_end = pos

        name = cp.utf8(name_index)
        desc = cp.utf8(desc_index)
        if name == "<init>" and desc == constructor_desc:
            methods.append(method_info(
                access_flags, name_index, desc_index, code_name,
                bytes(constructor_code), 3, 2))
        elif name == "actionPerformed" and desc == action_desc:
            methods.append(method_info(
                access_flags, name_index, desc_index, code_name,
                bytes(action_code), 1, 2))
        else:
            methods.append(original[method_start:method_end])

    class_attributes = original[pos:]

    patched = bytearray()
    patched.extend(original[:8])
    patched.extend(cp.bytes())
    patched.extend(original[class_body_prefix_start:methods_count_pos])
    patched.extend(u2(methods_count))
    for method in methods:
        patched.extend(method)
    patched.extend(class_attributes)
    return bytes(patched)


def class_files_to_add():
    files = {}
    package_dir = CLASSES_DIR / "com/amazon/ebook/booklet/reader/plugin/notesandmarks"
    for path in sorted(package_dir.glob("*.class")):
        entry_name = str(path.relative_to(CLASSES_DIR))
        if entry_name.endswith("AnnotationsSelectionDialogProvider$AddNoteAction.class"):
            continue
        files[entry_name] = path.read_bytes()
    return files


def write_generated_entry(target, name, data):
    entry = zipfile.ZipInfo(name, GENERATED_ZIP_TIMESTAMP)
    entry.compress_type = zipfile.ZIP_DEFLATED
    entry.create_system = 3
    entry.external_attr = 0o100644 << 16
    target.writestr(entry, data)


def build_jar():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ORIGINAL_JAR, "r") as source:
        original_class = source.read(ADD_NOTE_CLASS + ".class")
        patched_class = patch_add_note_class(original_class)
        content_panel_class = patch_content_panel_book_tap_class(
            source.read(CONTENT_PANEL_MOUSE_CLASS + ".class")
        )
        extra_classes = class_files_to_add()
        if AUTO_UNDO_CLASS + ".class" not in extra_classes:
            raise ValueError(
                "compiled AutoUndoSelectionSession.class is required for book-tap hook"
            )

        tmp_jar = OUT_JAR.with_suffix(".jar.tmp")
        with zipfile.ZipFile(tmp_jar, "w", zipfile.ZIP_DEFLATED) as target:
            for item in source.infolist():
                data = source.read(item.filename)
                if item.filename == ADD_NOTE_CLASS + ".class":
                    data = patched_class
                elif item.filename == CONTENT_PANEL_MOUSE_CLASS + ".class":
                    data = content_panel_class
                target.writestr(item, data)
            for name, data in extra_classes.items():
                write_generated_entry(target, name, data)
        shutil.move(str(tmp_jar), str(OUT_JAR))

    share_class_path = CLASSES_DIR / (SHARE_ACTION_CLASS + ".class")
    if not share_class_path.is_file():
        raise ValueError(
            "compiled ShareSelectedTextAction.class is required for Translate auto undo"
        )

    with zipfile.ZipFile(ORIGINAL_READER_JAR, "r") as source:
        tmp_reader_jar = OUT_READER_JAR.with_suffix(".jar.tmp")
        replaced = False
        with zipfile.ZipFile(tmp_reader_jar, "w", zipfile.ZIP_DEFLATED) as target:
            for item in source.infolist():
                data = source.read(item.filename)
                if item.filename == SHARE_ACTION_CLASS + ".class":
                    data = share_class_path.read_bytes()
                    replaced = True
                target.writestr(item, data)
        if not replaced:
            tmp_reader_jar.unlink(missing_ok=True)
            raise ValueError(
                "original Reader-plugin.jar does not contain ShareSelectedTextAction.class"
            )
        shutil.move(str(tmp_reader_jar), str(OUT_READER_JAR))

    print(OUT_JAR)
    print(OUT_READER_JAR)


if __name__ == "__main__":
    try:
        build_jar()
    except Exception as exc:
        print(f"build_ai_note_patch.py failed: {exc}", file=sys.stderr)
        sys.exit(1)
