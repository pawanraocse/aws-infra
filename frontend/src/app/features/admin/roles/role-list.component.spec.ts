import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RoleListComponent } from './role-list.component';
import { RoleService } from '../../../core/services/role.service';
import { DialogService } from 'primeng/dynamicdialog';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { NO_ERRORS_SCHEMA, provideZonelessChangeDetection } from '@angular/core';

describe('RoleListComponent', () => {
    let component: RoleListComponent;
    let fixture: ComponentFixture<RoleListComponent>;
    let roleServiceSpy: jasmine.SpyObj<RoleService>;
    let dialogServiceSpy: jasmine.SpyObj<DialogService>;
    let messageServiceSpy: jasmine.SpyObj<MessageService>;

    beforeEach(async () => {
        const rSpy = jasmine.createSpyObj('RoleService', ['getRoles']);
        const dSpy = jasmine.createSpyObj('DialogService', ['open']);
        const mSpy = jasmine.createSpyObj('MessageService', ['add']);

        await TestBed.configureTestingModule({
            imports: [RoleListComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: RoleService, useValue: rSpy },
                { provide: DialogService, useValue: dSpy },
                { provide: MessageService, useValue: mSpy }
            ],
            schemas: [NO_ERRORS_SCHEMA]
        }).compileComponents();

        roleServiceSpy = TestBed.inject(RoleService) as jasmine.SpyObj<RoleService>;
        dialogServiceSpy = TestBed.inject(DialogService) as jasmine.SpyObj<DialogService>;
        messageServiceSpy = TestBed.inject(MessageService) as jasmine.SpyObj<MessageService>;

        roleServiceSpy.getRoles.and.returnValue(of([]));

        fixture = TestBed.createComponent(RoleListComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load roles on init', () => {
        expect(roleServiceSpy.getRoles).toHaveBeenCalled();
    });
});
