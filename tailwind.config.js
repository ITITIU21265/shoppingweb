/** @type {import('tailwindcss').Config} */
module.exports = {
  corePlugins: {
    preflight: false
  },
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        primary: "var(--primary)",
        "background-light": "var(--surface-soft)",
        "background-dark": "var(--text)"
      },
      fontFamily: {
        display: ["Manrope", "sans-serif"]
      }
    }
  },
  plugins: [require("@tailwindcss/forms"), require("@tailwindcss/container-queries")]
}
