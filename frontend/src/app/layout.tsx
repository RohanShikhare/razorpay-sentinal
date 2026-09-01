import type { Metadata } from "next";
import "./globals.css";
import { Header } from "@/components/layout/Header";

export const metadata: Metadata = {
  title: "Razorpay Sentinel — AI Payment Incident Commander",
  description:
    "Detects abnormal payment failures, diagnoses root cause, quantifies revenue at risk, and recommends a bounded, policy-gated recovery strategy.",
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body className="min-h-screen bg-base font-sans text-ink antialiased">
        <Header />
        {children}
      </body>
    </html>
  );
}
