import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';

type SkeletonRadius = 'sm' | 'md' | 'lg' | 'xl' | '2xl' | 'full';

@Component({
  standalone: true,
  selector: 'ui-skeleton',
  imports: [CommonModule],
  templateUrl: './skeleton.component.html',
  styleUrls: ['./skeleton.component.scss'],
})
export class UiSkeletonComponent {
  @Input() w: string = '100%'; // ex: '120px', '100%', '16rem'
  @Input() h: string = '12px'; // ex: '12px', '44px'
  @Input() radius: SkeletonRadius = '2xl';
  @Input() className = '';

  get radiusClass(): string {
    switch (this.radius) {
      case 'sm':
        return 'rounded-sm';
      case 'md':
        return 'rounded-md';
      case 'lg':
        return 'rounded-lg';
      case 'xl':
        return 'rounded-xl';
      case '2xl':
        return 'rounded-2xl';
      case 'full':
        return 'rounded-full';
      default:
        return 'rounded-2xl';
    }
  }
}
