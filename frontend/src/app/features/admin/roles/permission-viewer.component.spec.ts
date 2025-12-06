import { ComponentFixture, TestBed } from '@angular/core/testing';
import { PermissionViewerComponent } from './permission-viewer.component';
import { RoleService } from '../../../core/services/role.service';
import { DynamicDialogConfig } from 'primeng/dynamicdialog';
import { of } from 'rxjs';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideZonelessChangeDetection } from '@angular/core';

describe('PermissionViewerComponent', () => {
    let component: PermissionViewerComponent;
    let fixture: ComponentFixture<PermissionViewerComponent>;
    let roleServiceSpy: jasmine.SpyObj<RoleService>;

    beforeEach(async () => {
        const rSpy = jasmine.createSpyObj('RoleService', ['getPermissions']);

        await TestBed.configureTestingModule({
            imports: [PermissionViewerComponent],
            providers: [
                provideZonelessChangeDetection(),
                provideHttpClient(),
                provideHttpClientTesting(),
                provideAnimations(),
                { provide: RoleService, useValue: rSpy },
                { provide: DynamicDialogConfig, useValue: { data: { roleId: 'test-role' } } }
            ]
        }).compileComponents();

        roleServiceSpy = TestBed.inject(RoleService) as jasmine.SpyObj<RoleService>;
        roleServiceSpy.getPermissions.and.returnValue(of([]));

        fixture = TestBed.createComponent(PermissionViewerComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });

    it('should load permissions on init', () => {
        expect(roleServiceSpy.getPermissions).toHaveBeenCalled();
    });
});
