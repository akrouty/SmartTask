import { Component } from '@angular/core';
import { RouterOutlet, Router } from '@angular/router';
import { GlobalLoaderComponent } from './shared/ui/global-loader/global-loader.component';
import { LoadingService } from './core/loading.service';

@Component({
  standalone: true,
  selector: 'app-root',
  imports: [RouterOutlet, GlobalLoaderComponent],
  template: `
    <app-global-loader></app-global-loader>
    <router-outlet></router-outlet>
  `,
})
export class AppComponent {
  constructor(router: Router, loading: LoadingService) {
    loading.bindRouter(router);
  }
}
