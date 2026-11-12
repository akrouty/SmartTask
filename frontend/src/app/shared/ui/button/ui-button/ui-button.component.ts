import { CommonModule } from '@angular/common';
import { ChangeDetectionStrategy, Component, Input } from '@angular/core';

type ButtonVariant = 'primary' | 'secondary' | 'ghost' | 'danger';
type ButtonSize = 'sm' | 'md' | 'lg';

@Component({
  selector: 'ui-button',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './ui-button.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class UiButtonComponent {
  @Input() variant: ButtonVariant = 'primary';
  @Input() size: ButtonSize = 'md';
  @Input() type: 'button' | 'submit' = 'button';
  @Input() disabled = false;
  @Input() loading = false;
  @Input() fullWidth = false;

  get isDisabled() {
    return this.disabled || this.loading;
  }

  get classes(): string {
    const base =
      'inline-flex items-center justify-center gap-2 rounded-xl font-semibold ' +
      'focus:outline-none focus:ring-2 focus:ring-indigo-200 dark:focus:ring-indigo-500/30 ' +
      'transition-all duration-200 select-none';

    const sizes: Record<ButtonSize, string> = {
      sm: 'px-3 py-2 text-xs',
      md: 'px-4 py-2.5 text-sm',
      lg: 'px-5 py-3 text-sm',
    };

    const variants: Record<ButtonVariant, string> = {
      primary:
        'bg-indigo-600 text-white hover:bg-indigo-900 active:bg-indigo-800 ' +
        'shadow-sm hover:shadow-md',
      secondary:
        'border border-indigo-200 dark:border-indigo-500/30 bg-white dark:bg-slate-950 ' +
        'text-indigo-600 dark:text-indigo-100 hover:bg-indigo-200 dark:hover:bg-indigo-500/10 ' +
        'active:bg-indigo-100 dark:active:bg-indigo-500/20',
      ghost:
        'bg-transparent text-slate-700 dark:text-slate-200 ' +
        'hover:bg-slate-100 dark:hover:bg-slate-900/60',
      danger: 'bg-red-600 text-white hover:bg-red-700 active:bg-red-800',
    };

    const width = this.fullWidth ? 'w-full' : '';
    const state = this.isDisabled ? 'opacity-60 cursor-not-allowed' : '';

    return [base, sizes[this.size], variants[this.variant], width, state].join(
      ' '
    );
  }
}
