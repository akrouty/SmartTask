import {
  Directive,
  ElementRef,
  Input,
  OnChanges,
  SimpleChanges,
} from '@angular/core';

@Directive({
  selector: '[uiCountUp]',
  standalone: true,
})
export class CountUpDirective implements OnChanges {
  /** Valeur finale (obligatoire) */
  @Input() uiCountUp = 0;

  /** Durée de l’animation en ms */
  @Input() duration = 900;

  /** Nombre de décimales */
  @Input() decimals = 0;

  /** Texte avant/après (ex: + / %) */
  @Input() prefix = '';
  @Input() suffix = '';

  /** Séparateur milliers (ex: 12 345) */
  @Input() useGrouping = true;

  private rafId?: number;

  constructor(private el: ElementRef<HTMLElement>) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (!('uiCountUp' in changes)) return;

    const end = Number(this.uiCountUp);
    if (!Number.isFinite(end)) {
      this.el.nativeElement.textContent = `${this.prefix}${this.uiCountUp}${this.suffix}`;
      return;
    }

    this.start(end);
  }

  private start(end: number) {
    if (this.rafId) cancelAnimationFrame(this.rafId);

    const start = 0;
    const startTime = performance.now();

    const tick = (now: number) => {
      const t = Math.min(1, (now - startTime) / this.duration);
      // easing (plus smooth)
      const eased = 1 - Math.pow(1 - t, 3);

      const value = start + (end - start) * eased;

      const formatted = new Intl.NumberFormat(undefined, {
        minimumFractionDigits: this.decimals,
        maximumFractionDigits: this.decimals,
        useGrouping: this.useGrouping,
      }).format(value);

      this.el.nativeElement.textContent = `${this.prefix}${formatted}${this.suffix}`;

      if (t < 1) this.rafId = requestAnimationFrame(tick);
    };

    this.rafId = requestAnimationFrame(tick);
  }
}
