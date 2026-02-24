import { Injectable, signal, effect } from '@angular/core';

@Injectable({
  providedIn: 'root'
})
export class ThemeService {
  theme = signal<'dark' | 'light'>('dark');

  constructor() {
    const stored = localStorage.getItem('theme') as 'dark' | 'light' | null;
    if (stored) {
      this.theme.set(stored);
    }

    effect(() => {
      const currentTheme = this.theme();
      localStorage.setItem('theme', currentTheme);
      if (currentTheme === 'light') {
        document.documentElement.dataset['theme'] = 'light';
      } else {
        delete document.documentElement.dataset['theme'];
      }
    });
  }

  toggleTheme() {
    this.theme.set(this.theme() === 'dark' ? 'light' : 'dark');
  }

  initialize() {
    const stored = localStorage.getItem('theme') as 'dark' | 'light' | null;
    if (stored === 'light') {
      document.documentElement.dataset['theme'] = 'light';
    }
  }
}
