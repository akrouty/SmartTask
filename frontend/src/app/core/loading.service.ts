import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import {
  Router,
  NavigationStart,
  NavigationEnd,
  NavigationCancel,
  NavigationError,
} from '@angular/router';
import { filter } from 'rxjs/operators';

export type LoaderMode = 'bar' | 'overlay';

export interface LoaderState {
  active: boolean;
  mode: LoaderMode;
  message?: string;
}

@Injectable({ providedIn: 'root' })
export class LoadingService {
  private count = 0;

  private readonly _state$ = new BehaviorSubject<LoaderState>({
    active: false,
    mode: 'bar',
    message: undefined,
  });

  state$: Observable<LoaderState> = this._state$.asObservable();

  // Réglages “pro”
  private showDelayMs = 0; // n’affiche rien si c’est < 200ms
  private minVisibleMs = 650; // si affiché, rester visible au moins 350ms
  private overlayAfterMs = 700; // si ça dure, basculer vers overlay

  private showTimer: any = null;
  private overlayTimer: any = null;

  private lastShownAt = 0;

  private bound = false;

  start(message?: string) {
    this.count++;
    if (this.count > 1) return; // déjà en cours

    // reset timers
    this.clearTimers();

    // “pending” → on attend showDelayMs avant d’afficher
    this.showTimer = setTimeout(() => {
      this.lastShownAt = Date.now();
      this._state$.next({ active: true, mode: 'bar', message });

      // si ça continue longtemps → overlay
      this.overlayTimer = setTimeout(() => {
        const cur = this._state$.value;
        if (cur.active) {
          this._state$.next({ ...cur, mode: 'overlay' });
        }
      }, this.overlayAfterMs);
    }, this.showDelayMs);
  }

  stop() {
    if (this.count === 0) return;
    this.count--;

    if (this.count > 0) return; // autres requêtes encore en cours

    // Si jamais rien n’a été affiché (requête très rapide)
    if (!this._state$.value.active) {
      this.clearTimers();
      return;
    }

    const elapsed = Date.now() - this.lastShownAt;
    const remaining = Math.max(0, this.minVisibleMs - elapsed);

    setTimeout(() => {
      this.clearTimers();
      this._state$.next({ active: false, mode: 'bar', message: undefined });
    }, remaining);
  }

  private clearTimers() {
    if (this.showTimer) clearTimeout(this.showTimer);
    if (this.overlayTimer) clearTimeout(this.overlayTimer);
    this.showTimer = null;
    this.overlayTimer = null;
  }
  bindRouter(router: Router) {
    if (this.bound) return;
    this.bound = true;

    router.events
      .pipe(
        filter(
          (e) =>
            e instanceof NavigationStart ||
            e instanceof NavigationEnd ||
            e instanceof NavigationCancel ||
            e instanceof NavigationError
        )
      )
      .subscribe((e) => {
        if (e instanceof NavigationStart)
          this.start('Chargement de la page...');
        else this.stop();
      });
  }
}
