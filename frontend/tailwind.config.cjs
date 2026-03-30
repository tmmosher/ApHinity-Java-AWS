/** @type {import("tailwindcss").Config} */
const { corporate, forest } = require("daisyui/src/theming/themes");

module.exports = {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {
      extend: {
        keyframes: {
          "gradient-border": {
            "0%": { "background-position": "0% 50%" },
            "50%": { "background-position": "100% 50%" },
            "100%": { "background-position": "0% 50%" },
          },
        },
        animation: {
          "gradient-border": "gradient-border 6s ease infinite",
        },
      },
    }
  },
  plugins: [require("daisyui")],
  daisyui: {
    themes: [
      "corporate",
      {
        "forest-corporate": {
          ...forest,
          "--rounded-btn": corporate["--rounded-btn"]
        }
      }
    ]
  }
};

