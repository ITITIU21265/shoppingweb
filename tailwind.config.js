/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js",
  ],
  theme: {
    extend: {
      fontFamily: {
        display: ['"Montserrat"', "sans-serif"],
      },
      colors: {
        primary: "#ff4b2b",
      },
      boxShadow: {
        auth: "0 14px 28px rgba(0,0,0,0.25), 0 10px 10px rgba(0,0,0,0.22)",
      },
    },
  },
  plugins: [require("@tailwindcss/forms")],
};
