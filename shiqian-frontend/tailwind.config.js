/** @type {import('tailwindcss').Config} */
export default {
  content: [
    "./index.html",
    "./src/**/*.{vue,js,ts,jsx,tsx}",
  ],
  theme: {
    extend: {
      colors: {
        // “时迁”学术温暖学术调色板
        primary: {
          50: '#f0f9f7',
          100: '#dcf2ed',
          200: '#bce5db',
          300: '#8ed2c4',
          400: '#5bb7a6',
          500: '#0f766e', // 主品牌青绿
          600: '#0d5f57',
          700: '#0c4d47',
          800: '#0a3d38',
          900: '#0a322f',
        },
        accent: {
          500: '#d4a574', // 暖沙金
        },
        forest: '#14532d',
        paper: '#f8f5f0',
        ink: '#172026',
      },
      fontFamily: {
        sans: ['Inter', 'PingFang SC', 'Microsoft YaHei', 'system-ui', 'sans-serif'],
      },
    },
  },
  plugins: [],
}