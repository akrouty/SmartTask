import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { LoadingService } from '../../../core/loading.service';
import { Observable } from 'rxjs';

@Component({
  selector: 'app-global-loader',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './global-loader.component.html',
})
export class GlobalLoaderComponent {
  state$: Observable<any>;
  constructor(private loader: LoadingService) {
    this.state$ = this.loader.state$;
  }
}
