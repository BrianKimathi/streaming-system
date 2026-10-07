/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        streamx: {
          dark: '#0f172a',
          card: '#1e293b',
          red: '#e50914',
          accent: '#6366f1',
          gold: '#f59e0b',
        }
      }
    },
  },
  plugins: [],
}
