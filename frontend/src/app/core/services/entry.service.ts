import { Injectable, inject } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { environment } from '../../../environments/environment';

export interface Entry {
    id: string;
    key: string;
    value: string;
    createdBy: string;
    createdAt: string;
}

export interface Page<T> {
    content: T[];
    totalElements: number;
    totalPages: number;
    size: number;
    number: number;
}

@Injectable({ providedIn: 'root' })
export class EntryService {
    private http = inject(HttpClient);
    private apiUrl = `${environment.apiUrl}/api/v1/entries`;

    getEntries(page = 0, size = 10): Observable<Page<Entry>> {
        const params = new HttpParams()
            .set('page', page.toString())
            .set('size', size.toString())
            .set('sort', 'createdAt,desc');

        return this.http.get<Page<Entry>>(this.apiUrl, { params });
    }

    createEntry(entry: { key: string; value: string }): Observable<Entry> {
        return this.http.post<Entry>(this.apiUrl, entry);
    }

    updateEntry(id: string, entry: { key: string; value: string }): Observable<Entry> {
        return this.http.put<Entry>(`${this.apiUrl}/${id}`, entry);
    }

    deleteEntry(id: string): Observable<void> {
        return this.http.delete<void>(`${this.apiUrl}/${id}`);
    }
}
