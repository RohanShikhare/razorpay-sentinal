import type { Config } from "tailwindcss";

const config: Config = {
  content: ["./src/**/*.{js,ts,jsx,tsx,mdx}"],
  theme: {
    extend: {
      colors: {
        base: {
          DEFAULT: "#0E1320",
          panel: "#151C2C",
          raised: "#1B2436",
          border: "#293347",
        },
        ink: {
          DEFAULT: "#E7EBF3",
          muted: "#8B95AC",
          faint: "#5C667D",
        },
        signal: {
          nominal: "#3FBBA3",
          watch: "#E8A33D",
          critical: "#E15A45",
          wire: "#4C8DFF",
        },
      },
      fontFamily: {
        sans: [
          "Söhne",
          "Inter",
          "-apple-system",
          "Segoe UI",
          "ui-sans-serif",
          "system-ui",
          "sans-serif",
        ],
        mono: [
          "IBM Plex Mono",
          "ui-monospace",
          "SFMono-Regular",
          "Menlo",
          "Consolas",
          "monospace",
        ],
      },
      backgroundImage: {
        grid: "linear-gradient(to right, #1B2436 1px, transparent 1px), linear-gradient(to bottom, #1B2436 1px, transparent 1px)",
      },
      backgroundSize: {
        grid: "40px 40px",
      },
    },
  },
  plugins: [],
};

export default config;
