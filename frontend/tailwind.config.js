/** @type {import('tailwindcss').Config} */
export default {
  content: ['./index.html', './src/**/*.{ts,tsx}'],
  theme: {
    extend: {
      colors: {
        paper: '#eef1f4',
        surface: '#ffffff',
        ink: '#14202b',
        ink2: '#5b6b7a',
        ink3: '#8a98a6',
        rule: '#d5dde5',
        cobalt: '#2456e6',
        good: '#0ca30c',
        warn: '#fab219',
        serious: '#ec835a',
        critical: '#d03b3b',
      },
      fontFamily: {
        sans: ['"IBM Plex Sans KR"', '"IBM Plex Sans"', '"Apple SD Gothic Neo"', 'system-ui', 'sans-serif'],
        display: ['"IBM Plex Sans Condensed"', '"IBM Plex Sans KR"', 'system-ui', 'sans-serif'],
        mono: ['"IBM Plex Mono"', 'ui-monospace', 'SFMono-Regular', 'monospace'],
      },
    },
  },
  plugins: [],
}
