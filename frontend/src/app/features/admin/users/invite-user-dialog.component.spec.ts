import { ComponentFixture, TestBed } from '@angular/core/testing';
import { InviteUserDialogComponent } from './invite-user-dialog.component';
import { InvitationService } from '../../../core/services/invitation.service';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection } from '@angular/core';

describe('InviteUserDialogComponent', () => {
    let component: InviteUserDialogComponent;
    let fixture: ComponentFixture<InviteUserDialogComponent>;
    let invitationServiceSpy: jasmine.SpyObj<InvitationService>;
    let refSpy: jasmine.SpyObj<DynamicDialogRef>;

    beforeEach(async () => {
        const invSpy = jasmine.createSpyObj('InvitationService', ['createInvitation']);
        const dialogRefSpy = jasmine.createSpyObj('DynamicDialogRef', ['close']);

        await TestBed.configureTestingModule({
            imports: [InviteUserDialogComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: InvitationService, useValue: invSpy },
                { provide: DynamicDialogRef, useValue: dialogRefSpy }
            ]
        }).compileComponents();

        invitationServiceSpy = TestBed.inject(InvitationService) as jasmine.SpyObj<InvitationService>;
        refSpy = TestBed.inject(DynamicDialogRef) as jasmine.SpyObj<DynamicDialogRef>;

        fixture = TestBed.createComponent(InviteUserDialogComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should initialize form invalid', () => {
        expect(component.inviteForm.valid).toBeFalse();
    });

    it('should validate email', () => {
        const emailControl = component.inviteForm.get('email');
        emailControl?.setValue('invalid-email');
        expect(emailControl?.valid).toBeFalse();
        emailControl?.setValue('test@example.com');
        expect(emailControl?.valid).toBeTrue();
    });
});
