import { Injectable, Inject } from '@angular/core';
import { DOCUMENT } from '@angular/common';

type Theme = 'light' | 'dark';

@Injectable({ providedIn: 'root' })
export class ThemeService {
  private key = 'smarttask_theme';
  private theme: Theme = 'light';

  constructor(@Inject(DOCUMENT) private doc: Document) {}

  init() {
    const saved = localStorage.getItem(this.key) as Theme | null;
    if (saved) {
      this.theme = saved;
    } else {
      const prefersDark = window.matchMedia?.(
        '(prefers-color-scheme: dark)'
      ).matches;
      this.theme = prefersDark ? 'dark' : 'light';
    }
    this.apply();
  }

  isDark() {
    return this.theme === 'dark';
  }

  toggle() {
    this.theme = this.theme === 'dark' ? 'light' : 'dark';
    localStorage.setItem(this.key, this.theme);
    this.apply();
  }
  private apply() {
    const root = this.doc.documentElement;

    root.classList.add('theme-transition');

    root.classList.toggle('dark', this.theme === 'dark');

    window.setTimeout(() => {
      root.classList.remove('theme-transition');
    }, 300);
  }
}
