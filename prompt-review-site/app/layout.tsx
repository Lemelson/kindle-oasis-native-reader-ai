import type { Metadata } from "next";
import "./globals.css";

export const metadata: Metadata = {
  title: "Kindle AI · Prompt Atlas",
  description: "Локальный обзор режимов, промптов и данных Kindle Native Reader AI.",
};

export default function RootLayout({
  children,
}: Readonly<{ children: React.ReactNode }>) {
  return (
    <html lang="ru">
      <body>{children}</body>
    </html>
  );
}
