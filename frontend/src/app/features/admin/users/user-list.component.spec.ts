import { ComponentFixture, TestBed } from '@angular/core/testing';
import { UserListComponent } from './user-list.component';
import { InvitationService } from '../../../core/services/invitation.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection, NO_ERRORS_SCHEMA } from '@angular/core';

describe('UserListComponent', () => {
    let component: UserListComponent;
    let fixture: ComponentFixture<UserListComponent>;
    let invitationServiceSpy: jasmine.SpyObj<InvitationService>;
    let dialogServiceSpy: jasmine.SpyObj<DialogService>;

    beforeEach(async () => {
        const invSpy = jasmine.createSpyObj('InvitationService', ['getInvitations', 'revokeInvitation', 'resendInvitation']);
        const dialogSpy = jasmine.createSpyObj('DialogService', ['open']);

        await TestBed.configureTestingModule({
            imports: [UserListComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: InvitationService, useValue: invSpy },
                { provide: DialogService, useValue: dialogSpy },
                { provide: MessageService, useValue: jasmine.createSpyObj('MessageService', ['add']) }
            ],
            schemas: [NO_ERRORS_SCHEMA]
        })
            .overrideComponent(UserListComponent, {
                set: {
                    providers: [
                        { provide: DialogService, useValue: dialogSpy },
                        { provide: MessageService, useValue: jasmine.createSpyObj('MessageService', ['add']) }
                    ]
                }
            })
            .compileComponents();

        invitationServiceSpy = TestBed.inject(InvitationService) as jasmine.SpyObj<InvitationService>;
        dialogServiceSpy = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;

        invitationServiceSpy.getInvitations.and.returnValue(of([]));

        fixture = TestBed.createComponent(UserListComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load invitations on init', () => {
        expect(invitationServiceSpy.getInvitations).toHaveBeenCalled();
    });

    it('should open invite dialog', () => {
        component.openInviteDialog();
        expect(dialogServiceSpy.open).toHaveBeenCalled();
    });
});
