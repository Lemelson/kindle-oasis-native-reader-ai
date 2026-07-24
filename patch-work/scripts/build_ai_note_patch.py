#!/usr/bin/env python3
import shutil
import struct
import sys
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[2]
ORIGINAL_JAR = ROOT / "from-kindle/native-reader-research/jars/ReaderSDK-impl.jar"
CLASSES_DIR = ROOT / "patch-work/classes-ai-v25"
OUT_DIR = ROOT / "patch-work/patched-ai-v25"
OUT_JAR = OUT_DIR / "ReaderSDK-impl.ai-v25.jar"

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


def build_jar():
    OUT_DIR.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(ORIGINAL_JAR, "r") as source:
        original_class = source.read(ADD_NOTE_CLASS + ".class")
        patched_class = patch_add_note_class(original_class)
        extra_classes = class_files_to_add()

        tmp_jar = OUT_JAR.with_suffix(".jar.tmp")
        with zipfile.ZipFile(tmp_jar, "w", zipfile.ZIP_DEFLATED) as target:
            for item in source.infolist():
                data = source.read(item.filename)
                if item.filename == ADD_NOTE_CLASS + ".class":
                    data = patched_class
                target.writestr(item, data)
            for name, data in extra_classes.items():
                target.writestr(name, data)
        shutil.move(str(tmp_jar), str(OUT_JAR))
    print(OUT_JAR)


if __name__ == "__main__":
    try:
        build_jar()
    except Exception as exc:
        print(f"build_ai_note_patch.py failed: {exc}", file=sys.stderr)
        sys.exit(1)
