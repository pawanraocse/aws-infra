import {Component} from '@angular/core';
import {CommonModule} from '@angular/common';
import {DialogModule} from 'primeng/dialog';
import {ButtonModule} from 'primeng/button';
import {InputTextModule} from 'primeng/inputtext';
import {TooltipModule} from 'primeng/tooltip';
import {FormsModule} from '@angular/forms';

@Component({
  selector: 'app-share-dialog',
  standalone: true,
  imports: [
    CommonModule,
    DialogModule,
    ButtonModule,
    InputTextModule,
    TooltipModule,
    FormsModule
  ],
  template: `
    <p-dialog
      [(visible)]="visible"
      [modal]="true"
      [draggable]="false"
      [resizable]="false"
      [style]="{ width: '400px' }"
      header="Share Application">

      <div class="flex flex-column gap-3">
        <div class="text-center mb-2">
          <i class="pi pi-heart text-pink-500 text-4xl mb-2"></i>
          <h3 class="m-0 text-900">Love using SaaS Factory?</h3>
          <p class="text-600 m-0 mt-1">Share it with your friends and colleagues!</p>
        </div>

        <div class="flex flex-column gap-2">
          <label for="shareLink" class="font-bold text-900">Share Link</label>
          <div class="p-inputgroup">
            <input
              pInputText
              id="shareLink"
              [readonly]="true"
              [value]="shareUrl"
              class="w-full"
            />
            <button
              pButton
              icon="pi pi-copy"
              (click)="copyLink()"
              pTooltip="Copy to clipboard"
              tooltipPosition="top"
              severity="secondary">
            </button>
          </div>
          <small *ngIf="copied" class="text-green-600 font-semibold fadein animation-duration-300">
            <i class="pi pi-check-circle mr-1"></i> Copied to clipboard!
          </small>
        </div>

        <div class="divider text-center text-500 relative my-2">
          <span class="bg-white px-2 relative z-1">OR</span>
          <div class="absolute w-full border-top-1 surface-border top-50 left-0"></div>
        </div>

        <button
          pButton
          label="Share via Email"
          icon="pi pi-envelope"
          class="w-full p-button-outlined"
          (click)="shareViaEmail()">
        </button>
      </div>
    </p-dialog>
  `,
  styles: [`
    .divider {
      height: 1rem;
      display: flex;
      align-items: center;
      justify-content: center;
    }
  `]
})
export class ShareDialogComponent {
  visible = false;
  copied = false;

  // Use current window location origin + signup path
  // In production this might be different if hosted on subdomain
  readonly shareUrl = window.location.origin + '/#/auth/signup';

  show() {
    this.visible = true;
    this.copied = false;
  }

  copyLink() {
    navigator.clipboard.writeText(this.shareUrl).then(() => {
      this.copied = true;
      setTimeout(() => this.copied = false, 2000);
    });
  }

  shareViaEmail() {
    const subject = encodeURIComponent('Check out this amazing app!');
    const body = encodeURIComponent(`Hey,\n\nI've been using this app and thought you'd like it:\n${this.shareUrl}\n\nCheers!`);
    window.open(`mailto:?subject=${subject}&body=${body}`, '_blank');
  }
}
