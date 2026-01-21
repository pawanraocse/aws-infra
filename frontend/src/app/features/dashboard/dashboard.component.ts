import {Component, inject, OnInit, signal} from '@angular/core';
import {CommonModule} from '@angular/common';
import {FormBuilder, FormsModule, ReactiveFormsModule, Validators} from '@angular/forms';
import {Router} from '@angular/router';
import {AuthService} from '../../core/auth.service';
import {Entry, EntryService} from '../../core/services/entry.service';
import {CardModule} from 'primeng/card';
import {TableModule} from 'primeng/table';
import {ButtonModule} from 'primeng/button';
import {DialogModule} from 'primeng/dialog';
import {InputTextModule} from 'primeng/inputtext';
import {MessageModule} from 'primeng/message';
import {AvatarModule} from 'primeng/avatar';
import {TagModule} from 'primeng/tag';

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule,
    CardModule, TableModule, ButtonModule, DialogModule, InputTextModule, MessageModule, TagModule, AvatarModule
  ],
  templateUrl: './dashboard.component.html',
  styleUrls: ['./dashboard.component.scss']
})
export class DashboardComponent implements OnInit {
  authService = inject(AuthService);
  private entryService = inject(EntryService);
  private fb = inject(FormBuilder);
  private router = inject(Router);

  entries = signal<Entry[]>([]);
  totalRecords = signal(0);
  loading = signal(false);

  // Dialog state
  displayDialog = signal(false);
  dialogHeader = signal('New Entry');
  saving = signal(false);
  error = signal<string | null>(null);

  entryForm = this.fb.group({
    id: [''],
    key: ['', Validators.required],
    value: ['', Validators.required]
  });

  ngOnInit() {
    this.loadEntries();

  }


  loadEntries(event?: any) {
    this.loading.set(true);
    const page = event ? event.first / event.rows : 0;
    const size = event ? event.rows : 10;

    this.entryService.getEntries(page, size).subscribe({
      next: (pageData) => {
        this.entries.set(pageData.content);
        this.totalRecords.set(pageData.totalElements);
        this.loading.set(false);
      },
      error: (err) => {
        console.error('Failed to load entries', err);
        this.loading.set(false);
      }
    });
  }

  showCreateDialog() {
    this.dialogHeader.set('New Entry');
    this.entryForm.reset();
    this.displayDialog.set(true);
    this.error.set(null);
  }

  editEntry(entry: Entry) {
    this.dialogHeader.set('Edit Entry');
    this.entryForm.patchValue(entry);
    this.displayDialog.set(true);
    this.error.set(null);
  }

  deleteEntry(entry: Entry) {
    if (!confirm('Are you sure you want to delete this entry?')) return;

    this.loading.set(true);
    this.entryService.deleteEntry(entry.id).subscribe({
      next: () => {
        this.loadEntries();
      },
      error: (err) => {
        alert('Failed to delete entry: ' + (err.error?.message || err.message));
        this.loading.set(false);
      }
    });
  }

  saveEntry() {
    if (this.entryForm.invalid) return;

    this.saving.set(true);
    this.error.set(null);
    const formValue = this.entryForm.value;

    const request = formValue.id
      ? this.entryService.updateEntry(formValue.id, { key: formValue.key!, value: formValue.value! })
      : this.entryService.createEntry({ key: formValue.key!, value: formValue.value! });

    request.subscribe({
      next: () => {
        this.displayDialog.set(false);
        this.saving.set(false);
        this.loadEntries();
      },
      error: (err) => {
        this.error.set(err.error?.message || 'Failed to save entry');
        this.saving.set(false);
      }
    });
  }
}

