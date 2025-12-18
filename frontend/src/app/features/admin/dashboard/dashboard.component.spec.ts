import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DashboardComponent } from './dashboard.component';
import { OrganizationService } from '../../../core/services/organization.service';
import { UserStatsService } from '../../../core/services/user-stats.service';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection } from '@angular/core';

describe('DashboardComponent', () => {
    let component: DashboardComponent;
    let fixture: ComponentFixture<DashboardComponent>;
    let organizationServiceSpy: jasmine.SpyObj<OrganizationService>;
    let userStatsServiceSpy: jasmine.SpyObj<UserStatsService>;
    let routerSpy: jasmine.SpyObj<Router>;

    beforeEach(async () => {
        const orgSpy = jasmine.createSpyObj('OrganizationService', ['getOrganization']);
        const statsSpy = jasmine.createSpyObj('UserStatsService', ['getUserStats']);
        const rSpy = jasmine.createSpyObj('Router', ['navigate']);

        await TestBed.configureTestingModule({
            imports: [DashboardComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: OrganizationService, useValue: orgSpy },
                { provide: UserStatsService, useValue: statsSpy },
                { provide: Router, useValue: rSpy }
            ]
        }).compileComponents();

        organizationServiceSpy = TestBed.inject(OrganizationService) as jasmine.SpyObj<OrganizationService>;
        userStatsServiceSpy = TestBed.inject(UserStatsService) as jasmine.SpyObj<UserStatsService>;
        routerSpy = TestBed.inject(Router) as jasmine.SpyObj<Router>;

        // Setup default mock responses
        organizationServiceSpy.getOrganization.and.returnValue(of({
            tenantId: 'test-tenant',
            name: 'Test Org',
            slaTier: 'STANDARD',
            tenantType: 'ORGANIZATION',
            maxUsers: 10,
            companyName: 'Test Company'
        }));

        userStatsServiceSpy.getUserStats.and.returnValue(of({
            totalUsers: 5,
            pendingInvitations: 2,
            expiredInvitations: 0,
            revokedInvitations: 0,
            roleDistribution: { 'admin': 2, 'user': 3 },
            adminCount: 2,
            regularUserCount: 3
        }));

        fixture = TestBed.createComponent(DashboardComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load dashboard data on init', () => {
        expect(organizationServiceSpy.getOrganization).toHaveBeenCalled();
        expect(userStatsServiceSpy.getUserStats).toHaveBeenCalled();
    });

    it('should navigate to settings', () => {
        component.navigateToSettings();
        expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin/settings/organization']);
    });

    it('should navigate to users', () => {
        component.navigateToUsers();
        expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin/users']);
    });

    it('should navigate to roles', () => {
        component.navigateToRoles();
        expect(routerSpy.navigate).toHaveBeenCalledWith(['/admin/roles']);
    });
});
