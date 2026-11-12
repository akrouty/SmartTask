import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, Output } from '@angular/core';

type CardPadding = 'none' | 'sm' | 'md' | 'lg' | 'xl';
type CardVariant = 'default' | 'soft' | 'outline' | 'ghost';
type CardAs = 'div' | 'button' | 'a';

@Component({
  selector: 'ui-card',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ui-card.component.html',
  styles: [':host{display:block;}'],
})
export class UiCardComponent {
  @Input('as') asTag: CardAs = 'div';
  @Input() href?: string;

  @Input() padding: CardPadding = 'md';
  @Input() variant: CardVariant = 'default';
  @Input() clickable = false;
  @Input() disabled = false;

  /** classes supplémentaires */
  @Input() className = '';

  @Output() cardClick = new EventEmitter<MouseEvent>();

  get computedClass(): string {
    const base = 'relative rounded-2xl border transition-all duration-200';

    const paddingMap: Record<CardPadding, string> = {
      none: '',
      sm: 'p-3',
      md: 'p-4',
      lg: 'p-5',
      xl: 'p-6',
    };

    const variantMap: Record<CardVariant, string> = {
      default:
        'bg-white dark:bg-slate-950/60 border-slate-100 dark:border-slate-800 shadow-sm',
      soft: 'bg-slate-50/70 dark:bg-slate-900/40 border-slate-200/70 dark:border-slate-800/70 shadow-sm',
      outline:
        'bg-transparent border-slate-200 dark:border-slate-800 shadow-none',
      ghost: 'bg-transparent border-transparent shadow-none',
    };

    const clickableCls =
      this.clickable || this.asTag !== 'div'
        ? 'cursor-pointer hover:shadow-md hover:-translate-y-[1px] focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-500/30'
        : '';

    const disabledCls = this.disabled ? 'opacity-60 cursor-not-allowed' : '';

    return [
      base,
      paddingMap[this.padding],
      variantMap[this.variant],
      clickableCls,
      disabledCls,
      this.className,
    ]
      .filter(Boolean)
      .join(' ');
  }

  emitClick(e: MouseEvent) {
    if (this.disabled) {
      e.preventDefault();
      e.stopPropagation();
      return;
    }

    // Si c'est un button/a, ou si clickable=true, on émet
    if (this.asTag !== 'div' || this.clickable) {
      this.cardClick.emit(e);
    }
  }

  emitKey(e: KeyboardEvent) {
    if (this.disabled) return;

    // on active au clavier uniquement si "clickable" (ou si c'est un lien/bouton)
    const interactive = this.clickable || this.asTag !== 'div';
    if (!interactive) return;

    if (e.key === 'Enter' || e.key === ' ') {
      e.preventDefault();
      this.cardClick.emit(new MouseEvent('click', { bubbles: true }));
    }
  }
}
