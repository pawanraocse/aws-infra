import { Component, inject, OnInit, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService, UserInfo } from '../../core/auth.service';
import { EntryApiService } from '../../core/services/entry-api.service';
import { EntryResponse, EntryRequest, PageResponse } from '../../shared/models/entry.model';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  private authService = inject(AuthService);
  private entryApi = inject(EntryApiService);

  // Expose Math for template
  Math = Math;

  // User info
  user = signal<UserInfo | null>(null);

  // Entry management
  entries = signal<EntryResponse[]>([]);
  totalEntries = signal<number>(0);
  currentPage = signal<number>(0);
  pageSize = signal<number>(10);
  loading = signal<boolean>(false);
  error = signal<string | null>(null);

  // Entry form
  showCreateForm = signal<boolean>(false);
  editingEntry = signal<EntryResponse | null>(null);
  entryForm = signal<EntryRequest>({ key: '', value: '' });

  ngOnInit(): void {
    // Load user info
    this.authService.getUserInfo().subscribe({
      next: (userInfo) => {
        this.user.set(userInfo);
      },
      error: (err) => {
        console.error('Failed to load user info', err);
      }
    });

    // Load entries
    this.loadEntries();
  }

  userInfo() {
    return this.user();
  }

  /**
   * Load entries with pagination
   */
  loadEntries(): void {
    this.loading.set(true);
    this.error.set(null);

    this.entryApi.getEntries({
      page: this.currentPage(),
      size: this.pageSize(),
      sort: 'createdAt,desc'
    }).subscribe({
      next: (page: PageResponse<EntryResponse>) => {
        this.entries.set(page.content);
        this.totalEntries.set(page.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load entries', err);
        this.error.set(err.message || 'Failed to load entries');
        this.loading.set(false);
      }
    });
  }

  /**
   * Create a new entry
   */
  createEntry(): void {
    const request = this.entryForm();

    if (!request.key || !request.value) {
      this.error.set('Key and value are required');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.entryApi.createEntry(request).subscribe({
      next: (entry) => {
        console.log('Entry created:', entry);
        this.showCreateForm.set(false);
        this.entryForm.set({ key: '', value: '' });
        this.loadEntries(); // Reload entries
      },
      error: (err) => {
        console.error('Failed to create entry', err);
        this.error.set(err.message || 'Failed to create entry');
        this.loading.set(false);
      }
    });
  }

  /**
   * Update an existing entry
   */
  updateEntry(): void {
    const entry = this.editingEntry();
    const request = this.entryForm();

    if (!entry || !request.key || !request.value) {
      this.error.set('Invalid entry data');
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.entryApi.updateEntry(entry.id, request).subscribe({
      next: (updated) => {
        console.log('Entry updated:', updated);
        this.editingEntry.set(null);
        this.entryForm.set({ key: '', value: '' });
        this.loadEntries(); // Reload entries
      },
      error: (err) => {
        console.error('Failed to update entry', err);
        this.error.set(err.message || 'Failed to update entry');
        this.loading.set(false);
      }
    });
  }

  /**
   * Delete an entry
   */
  deleteEntry(entry: EntryResponse): void {
    if (!confirm(`Are you sure you want to delete entry "${entry.key}"?`)) {
      return;
    }

    this.loading.set(true);
    this.error.set(null);

    this.entryApi.deleteEntry(entry.id).subscribe({
      next: () => {
        console.log('Entry deleted:', entry.id);
        this.loadEntries(); // Reload entries
      },
      error: (err) => {
        console.error('Failed to delete entry', err);
        this.error.set(err.message || 'Failed to delete entry');
        this.loading.set(false);
      }
    });
  }

  /**
   * Start editing an entry
   */
  startEdit(entry: EntryResponse): void {
    this.editingEntry.set(entry);
    this.entryForm.set({ key: entry.key, value: entry.value });
    this.showCreateForm.set(false);
  }

  /**
   * Cancel editing
   */
  cancelEdit(): void {
    this.editingEntry.set(null);
    this.entryForm.set({ key: '', value: '' });
  }

  /**
   * Toggle create form
   */
  toggleCreateForm(): void {
    this.showCreateForm.update(show => !show);
    this.editingEntry.set(null);
    this.entryForm.set({ key: '', value: '' });
    this.error.set(null);
  }

  /**
   * Go to next page
   */
  nextPage(): void {
    const totalPages = Math.ceil(this.totalEntries() / this.pageSize());
    if (this.currentPage() < totalPages - 1) {
      this.currentPage.update(page => page + 1);
      this.loadEntries();
    }
  }

  /**
   * Go to previous page
   */
  previousPage(): void {
    if (this.currentPage() > 0) {
      this.currentPage.update(page => page - 1);
      this.loadEntries();
    }
  }

  logout(): void {
    if (confirm('Are you sure you want to logout?')) {
      this.authService.logout().subscribe();
    }
  }
}

