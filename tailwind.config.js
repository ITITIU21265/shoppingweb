/** @type {import('tailwindcss').Config} */
module.exports = {
  content: [
    "./src/main/resources/templates/**/*.html",
    "./src/main/resources/static/js/**/*.js"
  ],
  theme: {
    extend: {
      colors: {
        primary: "#197fe6",
        "background-light": "#f6f7f8",
        "background-dark": "#111921"
      },
      fontFamily: {
        display: ["\"Plus Jakarta Sans\"", "sans-serif"]
      }
    }
  },
  plugins: [require("@tailwindcss/forms"), require("@tailwindcss/container-queries")]
}
