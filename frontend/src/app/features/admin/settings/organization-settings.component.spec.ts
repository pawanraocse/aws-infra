import { ComponentFixture, TestBed } from '@angular/core/testing';
import { OrganizationSettingsComponent } from './organization-settings.component';
import { OrganizationService } from '../../../core/services/organization.service';
import { MessageService } from 'primeng/api';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection, NO_ERRORS_SCHEMA } from '@angular/core';

describe('OrganizationSettingsComponent', () => {
    let component: OrganizationSettingsComponent;
    let fixture: ComponentFixture<OrganizationSettingsComponent>;
    let organizationServiceSpy: jasmine.SpyObj<OrganizationService>;
    let messageServiceSpy: jasmine.SpyObj<MessageService>;

    beforeEach(async () => {
        const orgSpy = jasmine.createSpyObj('OrganizationService', ['getOrganization', 'updateOrganization']);
        const msgSpy = jasmine.createSpyObj('MessageService', ['add']);

        await TestBed.configureTestingModule({
            imports: [OrganizationSettingsComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: OrganizationService, useValue: orgSpy },
                { provide: MessageService, useValue: msgSpy }
            ],
            schemas: [NO_ERRORS_SCHEMA]
        }).compileComponents();

        organizationServiceSpy = TestBed.inject(OrganizationService) as jasmine.SpyObj<OrganizationService>;
        messageServiceSpy = TestBed.inject(MessageService) as jasmine.SpyObj<MessageService>;

        organizationServiceSpy.getOrganization.and.returnValue(of({
            tenantId: 'test-tenant',
            name: 'Test Org',
            slaTier: 'STANDARD',
            tenantType: 'ORGANIZATION',
            maxUsers: 10,
            companyName: 'Test Company',
            industry: 'Technology',
            companySize: '11-50'
        }));

        fixture = TestBed.createComponent(OrganizationSettingsComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load organization on init', () => {
        expect(organizationServiceSpy.getOrganization).toHaveBeenCalled();
        expect(component.organization).toBeTruthy();
    });

    it('should update form with organization data', () => {
        expect(component.settingsForm.get('companyName')?.value).toBe('Test Company');
        expect(component.settingsForm.get('industry')?.value).toBe('Technology');
        expect(component.settingsForm.get('companySize')?.value).toBe('11-50');
    });

    it('should call update service when form is submitted', () => {
        organizationServiceSpy.updateOrganization.and.returnValue(of({
            tenantId: 'test-tenant',
            name: 'Test Org',
            slaTier: 'STANDARD',
            tenantType: 'ORGANIZATION',
            maxUsers: 10,
            companyName: 'Updated Company'
        }));

        component.settingsForm.patchValue({ companyName: 'Updated Company' });
        component.settingsForm.markAsDirty();
        component.saveSettings();

        expect(organizationServiceSpy.updateOrganization).toHaveBeenCalled();
    });
});
