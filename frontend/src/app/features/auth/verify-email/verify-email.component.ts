import {Component, ElementRef, OnDestroy, OnInit, QueryList, ViewChildren} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormsModule} from '@angular/forms';
import {ActivatedRoute, Router, RouterModule} from '@angular/router';
import {HttpClient} from '@angular/common/http';
import {ButtonModule} from 'primeng/button';
import {MessageModule} from 'primeng/message';
import {environment} from '../../../../environments/environment';

/**
 * Premium Email Verification Component.
 * Modern glassmorphism design with individual digit inputs and smooth animations.
 */
@Component({
  selector: 'app-verify-email',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule, ButtonModule, MessageModule],
  template: `
    <div class="verify-container">
      <!-- Animated background elements -->
      <div class="bg-decoration">
        <div class="circle circle-1"></div>
        <div class="circle circle-2"></div>
        <div class="circle circle-3"></div>
      </div>

      <div class="verify-content" [class.success-state]="verified">
        <div class="verify-card">
          <!-- Animated Icon -->
          <div class="icon-container" [class.verified]="verified" [class.error]="errorMessage">
            <div class="icon-ring">
              <i *ngIf="!verified" class="pi pi-envelope"></i>
              <i *ngIf="verified" class="pi pi-check"></i>
            </div>
            <div class="icon-pulse"></div>
          </div>

          <!-- Title -->
          <h1 class="verify-title">
            {{ verified ? 'Email Verified!' : 'Verify Your Email' }}
          </h1>

          <!-- Email Display -->
          <div class="email-badge" *ngIf="!verified">
            <i class="pi pi-at"></i>
            <span>{{ email }}</span>
          </div>

          <!-- Success State -->
          <div *ngIf="verified" class="success-content">
            <p class="success-message">
              Your email has been verified successfully.
            </p>
            <p class="redirect-message">
              Redirecting to login in {{ redirectCountdown }}s...
            </p>
          </div>

          <!-- Code Entry -->
          <div *ngIf="!verified" class="code-section">
            <p class="instruction-text">
              Enter the 6-digit code we sent to your email
            </p>

            <!-- Individual Digit Inputs -->
            <div class="code-inputs">
              <input
                *ngFor="let digit of codeDigits; let i = index"
                #digitInput
                type="text"
                inputmode="numeric"
                pattern="[0-9]*"
                maxlength="1"
                class="digit-input"
                [class.filled]="codeDigits[i]"
                [class.error]="errorMessage"
                [(ngModel)]="codeDigits[i]"
                (input)="onDigitInput(i, $event)"
                (keydown)="onKeyDown(i, $event)"
                (paste)="onPaste($event)"
                [disabled]="verifying"
              />
            </div>

            <!-- Messages -->
            <div class="message-container">
              <div *ngIf="successMessage" class="message success">
                <i class="pi pi-check-circle"></i>
                <span>{{ successMessage }}</span>
              </div>
              <div *ngIf="errorMessage" class="message error">
                <i class="pi pi-times-circle"></i>
                <span>{{ errorMessage }}</span>
              </div>
            </div>

            <!-- Verify Button -->
            <button
              pButton
              type="button"
              class="verify-btn"
              [loading]="verifying"
              [disabled]="!isCodeComplete() || verifying"
              (click)="verifyEmail()"
            >
              <i *ngIf="!verifying" class="pi pi-shield mr-2"></i>
              <span>{{ verifying ? 'Verifying...' : 'Verify Email' }}</span>
            </button>

            <!-- Resend Section -->
            <div class="resend-section">
              <p class="resend-text">Didn't receive the code?</p>
              <button
                pButton
                type="button"
                class="resend-btn"
                [text]="true"
                [loading]="resending"
                [disabled]="cooldownRemaining > 0 || resending"
                (click)="resendCode()"
              >
                <i class="pi pi-refresh mr-2"></i>
                {{ cooldownRemaining > 0 ? 'Resend in ' + cooldownRemaining + 's' : 'Resend Code' }}
              </button>
            </div>
          </div>

          <!-- Divider -->
          <div class="divider">
            <span>OR</span>
          </div>

          <!-- Back to Login -->
          <a routerLink="/auth/login" class="back-link">
            <i class="pi pi-arrow-left"></i>
            <span>Back to Login</span>
          </a>
        </div>

        <!-- Help Text -->
        <p class="help-text">
          Check your spam folder if you don't see the email
        </p>
      </div>
    </div>
  `,
  styles: [`
    /* ========== Container & Background ========== */
    .verify-container {
      display: flex;
      justify-content: center;
      align-items: center;
      min-height: 100vh;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 50%, #f093fb 100%);
      padding: 1rem;
      position: relative;
      overflow: hidden;
    }

    .bg-decoration {
      position: absolute;
      inset: 0;
      overflow: hidden;
      pointer-events: none;
    }

    .circle {
      position: absolute;
      border-radius: 50%;
      filter: blur(60px);
      opacity: 0.5;
      animation: float 20s infinite;
    }

    .circle-1 {
      width: 400px;
      height: 400px;
      background: rgba(255, 255, 255, 0.3);
      top: -100px;
      left: -100px;
      animation-delay: 0s;
    }

    .circle-2 {
      width: 300px;
      height: 300px;
      background: rgba(246, 211, 101, 0.3);
      bottom: -50px;
      right: -50px;
      animation-delay: -5s;
    }

    .circle-3 {
      width: 250px;
      height: 250px;
      background: rgba(255, 107, 107, 0.3);
      top: 50%;
      left: 50%;
      transform: translate(-50%, -50%);
      animation-delay: -10s;
    }

    @keyframes float {
      0%, 100% { transform: translateY(0) rotate(0deg); }
      50% { transform: translateY(-30px) rotate(10deg); }
    }

    /* ========== Content & Card ========== */
    .verify-content {
      position: relative;
      z-index: 1;
      width: 100%;
      max-width: 440px;
      animation: slideUp 0.6s ease-out;
    }

    @keyframes slideUp {
      from {
        opacity: 0;
        transform: translateY(30px);
      }
      to {
        opacity: 1;
        transform: translateY(0);
      }
    }

    .verify-card {
      background: rgba(255, 255, 255, 0.95);
      backdrop-filter: blur(20px);
      border-radius: 24px;
      border: 1px solid rgba(255, 255, 255, 0.5);
      box-shadow:
        0 25px 50px -12px rgba(0, 0, 0, 0.25),
        0 0 0 1px rgba(255, 255, 255, 0.1);
      padding: 3rem 2.5rem;
      text-align: center;
    }

    /* ========== Icon ========== */
    .icon-container {
      position: relative;
      display: inline-flex;
      justify-content: center;
      align-items: center;
      margin-bottom: 1.5rem;
    }

    .icon-ring {
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      position: relative;
      z-index: 1;
      transition: all 0.3s ease;

      i {
        font-size: 2rem;
        color: white;
      }
    }

    .icon-pulse {
      position: absolute;
      width: 80px;
      height: 80px;
      border-radius: 50%;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      animation: pulse 2s ease-out infinite;
    }

    @keyframes pulse {
      0% { transform: scale(1); opacity: 0.5; }
      100% { transform: scale(1.8); opacity: 0; }
    }

    .icon-container.verified .icon-ring {
      background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
    }

    .icon-container.verified .icon-pulse {
      background: linear-gradient(135deg, #4CAF50 0%, #45a049 100%);
    }

    .icon-container.error .icon-ring {
      animation: shake 0.4s ease;
    }

    @keyframes shake {
      0%, 100% { transform: translateX(0); }
      20%, 60% { transform: translateX(-5px); }
      40%, 80% { transform: translateX(5px); }
    }

    /* ========== Title & Email ========== */
    .verify-title {
      font-size: 1.75rem;
      font-weight: 700;
      color: #1a1a2e;
      margin: 0 0 1rem 0;
      letter-spacing: -0.02em;
    }

    .email-badge {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      background: linear-gradient(135deg, #f8f9ff 0%, #e8ebff 100%);
      padding: 0.75rem 1.25rem;
      border-radius: 50px;
      margin-bottom: 1.5rem;
      border: 1px solid rgba(102, 126, 234, 0.2);

      i {
        color: #667eea;
        font-size: 0.875rem;
      }

      span {
        font-weight: 600;
        color: #1a1a2e;
        font-size: 0.95rem;
      }
    }

    /* ========== Code Section ========== */
    .code-section {
      margin-top: 0.5rem;
    }

    .instruction-text {
      color: #6b7280;
      font-size: 0.95rem;
      margin-bottom: 1.5rem;
    }

    .code-inputs {
      display: flex;
      justify-content: center;
      gap: 0.75rem;
      margin-bottom: 1.5rem;
    }

    .digit-input {
      width: 52px;
      height: 64px;
      text-align: center;
      font-size: 1.75rem;
      font-weight: 700;
      color: #1a1a2e;
      border: 2px solid #e5e7eb;
      border-radius: 16px;
      background: #fff;
      transition: all 0.2s ease;
      outline: none;
      caret-color: #667eea;

      &:focus {
        border-color: #667eea;
        box-shadow: 0 0 0 4px rgba(102, 126, 234, 0.15);
        transform: translateY(-2px);
      }

      &.filled {
        border-color: #667eea;
        background: linear-gradient(135deg, #f8f9ff 0%, #fff 100%);
      }

      &.error {
        border-color: #ef4444;
        animation: shake 0.4s ease;
      }

      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
      }
    }

    /* ========== Messages ========== */
    .message-container {
      min-height: 2.5rem;
      margin-bottom: 1rem;
    }

    .message {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;
      padding: 0.75rem 1rem;
      border-radius: 12px;
      font-size: 0.9rem;
      font-weight: 500;
      animation: fadeIn 0.3s ease;

      &.success {
        background: linear-gradient(135deg, #ecfdf5 0%, #d1fae5 100%);
        color: #065f46;
        border: 1px solid #a7f3d0;
      }

      &.error {
        background: linear-gradient(135deg, #fef2f2 0%, #fee2e2 100%);
        color: #991b1b;
        border: 1px solid #fecaca;
      }
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(-10px); }
      to { opacity: 1; transform: translateY(0); }
    }

    /* ========== Buttons ========== */
    .verify-btn {
      width: 100%;
      padding: 1rem 2rem;
      font-size: 1rem;
      font-weight: 600;
      border: none;
      border-radius: 14px;
      background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
      color: white;
      cursor: pointer;
      transition: all 0.3s ease;
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 0.5rem;

      &:hover:not(:disabled) {
        transform: translateY(-2px);
        box-shadow: 0 10px 30px -5px rgba(102, 126, 234, 0.5);
      }

      &:disabled {
        opacity: 0.6;
        cursor: not-allowed;
        transform: none;
      }
    }

    /* ========== Resend Section ========== */
    .resend-section {
      margin-top: 1.5rem;
    }

    .resend-text {
      color: #9ca3af;
      font-size: 0.875rem;
      margin-bottom: 0.5rem;
    }

    .resend-btn {
      color: #667eea !important;
      font-weight: 600;

      &:hover:not(:disabled) {
        color: #764ba2 !important;
      }
    }

    /* ========== Divider ========== */
    .divider {
      display: flex;
      align-items: center;
      margin: 1.5rem 0;

      &::before,
      &::after {
        content: '';
        flex: 1;
        height: 1px;
        background: linear-gradient(90deg, transparent, #e5e7eb, transparent);
      }

      span {
        padding: 0 1rem;
        color: #9ca3af;
        font-size: 0.75rem;
        font-weight: 600;
        letter-spacing: 0.1em;
      }
    }

    /* ========== Back Link ========== */
    .back-link {
      display: inline-flex;
      align-items: center;
      gap: 0.5rem;
      color: #6b7280;
      text-decoration: none;
      font-weight: 500;
      transition: all 0.2s ease;
      padding: 0.75rem 1.25rem;
      border-radius: 12px;

      &:hover {
        color: #667eea;
        background: rgba(102, 126, 234, 0.08);
        transform: translateX(-4px);
      }

      i {
        font-size: 0.875rem;
        transition: transform 0.2s ease;
      }

      &:hover i {
        transform: translateX(-4px);
      }
    }

    /* ========== Help Text ========== */
    .help-text {
      color: rgba(255, 255, 255, 0.8);
      font-size: 0.875rem;
      text-align: center;
      margin-top: 1.5rem;
    }

    /* ========== Success State ========== */
    .success-content {
      animation: fadeIn 0.5s ease;
    }

    .success-message {
      color: #065f46;
      font-size: 1.1rem;
      font-weight: 500;
      margin-bottom: 0.5rem;
    }

    .redirect-message {
      color: #6b7280;
      font-size: 0.9rem;
    }

    .success-state .verify-card {
      border-color: rgba(16, 185, 129, 0.3);
    }

    /* ========== Responsive ========== */
    @media (max-width: 480px) {
      .verify-card {
        padding: 2rem 1.5rem;
        border-radius: 20px;
      }

      .code-inputs {
        gap: 0.5rem;
      }

      .digit-input {
        width: 44px;
        height: 56px;
        font-size: 1.5rem;
        border-radius: 12px;
      }

      .icon-ring,
      .icon-pulse {
        width: 64px;
        height: 64px;
      }

      .icon-ring i {
        font-size: 1.5rem;
      }

      .verify-title {
        font-size: 1.5rem;
      }
    }
  `]
})
export class VerifyEmailComponent implements OnInit, OnDestroy {
  @ViewChildren('digitInput') digitInputs!: QueryList<ElementRef>;

  email: string = '';
  tenantId: string = '';
  codeDigits: string[] = ['', '', '', '', '', ''];
  verifying: boolean = false;
  resending: boolean = false;
  verified: boolean = false;
  successMessage: string = '';
  errorMessage: string = '';
  cooldownRemaining: number = 0;
  redirectCountdown: number = 3;
  private cooldownInterval: any;
  private redirectInterval: any;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private http: HttpClient
  ) { }

  ngOnInit(): void {
    // Get email and tenantId from router state
    this.email = history.state?.email || this.route.snapshot.queryParams['email'];
    this.tenantId = history.state?.tenantId || '';

    console.log('VerifyEmailComponent initialized:', { email: this.email, tenantId: this.tenantId });

    // If no email provided, redirect to signup
    if (!this.email) {
      console.log('No email found, redirecting to signup');
      this.router.navigate(['/auth/signup/personal']);
    }
  }

  isCodeComplete(): boolean {
    return this.codeDigits.every(digit => digit !== '');
  }

  getVerificationCode(): string {
    return this.codeDigits.join('');
  }

  onDigitInput(index: number, event: Event): void {
    const input = event.target as HTMLInputElement;
    const value = input.value;

    // Only allow digits
    if (!/^\d*$/.test(value)) {
      this.codeDigits[index] = '';
      return;
    }

    // Take only the last character if multiple
    this.codeDigits[index] = value.slice(-1);

    // Clear error on input
    this.errorMessage = '';

    // Move to next input
    if (value && index < 5) {
      this.focusInput(index + 1);
    }
  }

  onKeyDown(index: number, event: KeyboardEvent): void {
    // Handle backspace
    if (event.key === 'Backspace') {
      if (!this.codeDigits[index] && index > 0) {
        this.focusInput(index - 1);
        this.codeDigits[index - 1] = '';
      }
    }

    // Handle arrow keys
    if (event.key === 'ArrowLeft' && index > 0) {
      this.focusInput(index - 1);
    }
    if (event.key === 'ArrowRight' && index < 5) {
      this.focusInput(index + 1);
    }
  }

  onPaste(event: ClipboardEvent): void {
    event.preventDefault();
    const pastedData = event.clipboardData?.getData('text') || '';
    const digits = pastedData.replace(/\D/g, '').slice(0, 6);

    for (let i = 0; i < 6; i++) {
      this.codeDigits[i] = digits[i] || '';
    }

    // Focus the last filled or first empty input
    const lastFilledIndex = digits.length - 1;
    if (lastFilledIndex >= 0 && lastFilledIndex < 5) {
      this.focusInput(lastFilledIndex + 1);
    }
  }

  private focusInput(index: number): void {
    setTimeout(() => {
      const inputs = this.digitInputs.toArray();
      if (inputs[index]) {
        inputs[index].nativeElement.focus();
        inputs[index].nativeElement.select();
      }
    }, 0);
  }

  verifyEmail(): void {
    if (!this.isCodeComplete()) return;

    this.verifying = true;
    this.errorMessage = '';
    this.successMessage = '';

    const payload = {
      email: this.email,
      code: this.getVerificationCode(),
      tenantId: this.tenantId
    };

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/signup/verify`, payload)
      .subscribe({
        next: (response: any) => {
          this.verifying = false;
          this.verified = true;
          this.startRedirectCountdown();
        },
        error: (err) => {
          this.verifying = false;
          this.errorMessage = err.error?.message || 'Verification failed. Please try again.';
        }
      });
  }

  private startRedirectCountdown(): void {
    this.redirectCountdown = 3;
    this.redirectInterval = setInterval(() => {
      this.redirectCountdown--;
      if (this.redirectCountdown <= 0) {
        clearInterval(this.redirectInterval);
        this.router.navigate(['/auth/login'], {
          queryParams: { verified: 'true', email: this.email }
        });
      }
    }, 1000);
  }

  resendCode(): void {
    if (this.cooldownRemaining > 0) return;

    this.resending = true;
    this.errorMessage = '';

    this.http.post(`${environment.apiUrl}/auth/api/v1/auth/resend-verification`, { email: this.email })
      .subscribe({
        next: () => {
          this.resending = false;
          this.successMessage = 'Verification code sent!';
          this.startCooldown(60);
          // Clear success message after 3 seconds
          setTimeout(() => this.successMessage = '', 3000);
        },
        error: (err) => {
          this.resending = false;
          this.errorMessage = err.error?.message || 'Failed to resend code.';
        }
      });
  }

  private startCooldown(seconds: number): void {
    this.cooldownRemaining = seconds;

    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
    }

    this.cooldownInterval = setInterval(() => {
      this.cooldownRemaining--;
      if (this.cooldownRemaining <= 0) {
        clearInterval(this.cooldownInterval);
      }
    }, 1000);
  }

  ngOnDestroy(): void {
    if (this.cooldownInterval) {
      clearInterval(this.cooldownInterval);
    }
    if (this.redirectInterval) {
      clearInterval(this.redirectInterval);
    }
  }
}
