import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { AppLayoutComponent } from './layout/app-layout.component';

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [RouterOutlet, AppLayoutComponent],
  template: `
    <app-layout>
      <main class="main">
        <h1>Hello</h1>
      </main>
      <router-outlet />
    </app-layout>
  `,
  styleUrls: ['./app.scss']
})
export class App {}
