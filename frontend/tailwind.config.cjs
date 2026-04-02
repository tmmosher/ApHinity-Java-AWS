/** @type {import("tailwindcss").Config} */
const { corporate, forest } = require("daisyui/src/theming/themes");

module.exports = {
  content: ["./index.html", "./src/**/*.{js,ts,jsx,tsx}"],
  theme: {
    extend: {}
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

