import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { BaseApiService } from '../base-api.service';
import { EntryRequest, EntryResponse, PageResponse, PageRequest } from '../../shared/models/entry.model';

/**
 * Entry API Service
 * 
 * Provides CRUD operations for Entry management.
 * Extends BaseApiService for common HTTP operations.
 * 
 * All methods automatically include JWT token via AuthInterceptor.
 * 
 * Usage:
 * ```typescript
 * constructor(private entryApi: EntryApiService) {}
 * 
 * // Get all entries (paginated)
 * this.entryApi.getEntries({ page: 0, size: 20 }).subscribe(page => {
 *   console.log(page.content);
 * });
 * 
 * // Create entry
 * this.entryApi.createEntry({ key: 'api_key', value: 'secret' }).subscribe(entry => {
 *   console.log('Created:', entry);
 * });
 * ```
 */
@Injectable({ providedIn: 'root' })
export class EntryApiService extends BaseApiService {
  
  constructor() {
    super('/api/v1/entries');
  }

  /**
   * Get paginated list of entries
   * 
   * @param pageRequest - Pagination parameters (page, size, sort)
   * @returns Observable of paginated entries
   * 
   * @example
   * getEntries({ page: 0, size: 20, sort: 'createdAt,desc' })
   */
  getEntries(pageRequest?: PageRequest): Observable<PageResponse<EntryResponse>> {
    const params: Record<string, any> = {};
    
    if (pageRequest?.page !== undefined) {
      params['page'] = pageRequest.page;
    }
    if (pageRequest?.size !== undefined) {
      params['size'] = pageRequest.size;
    }
    if (pageRequest?.sort) {
      params['sort'] = pageRequest.sort;
    }
    
    return this.getAll<PageResponse<EntryResponse>>(params);
  }

  /**
   * Get a single entry by ID
   * 
   * @param id - Entry ID
   * @returns Observable of entry or 404 error
   * 
   * @example
   * getEntry(123).subscribe(entry => console.log(entry))
   */
  getEntry(id: number): Observable<EntryResponse> {
    return this.getById<EntryResponse>(id);
  }

  /**
   * Create a new entry
   * 
   * @param request - Entry data (key, value)
   * @returns Observable of created entry with ID and audit fields
   * 
   * @example
   * createEntry({ key: 'api_key', value: 'sk-123' }).subscribe(entry => {
   *   console.log('Created entry with ID:', entry.id);
   * })
   */
  createEntry(request: EntryRequest): Observable<EntryResponse> {
    return this.create<EntryRequest, EntryResponse>(request);
  }

  /**
   * Update an existing entry
   * 
   * @param id - Entry ID
   * @param request - Updated entry data (key, value)
   * @returns Observable of updated entry or 404 error
   * 
   * @example
   * updateEntry(123, { key: 'api_key', value: 'new-value' }).subscribe(entry => {
   *   console.log('Updated:', entry);
   * })
   */
  updateEntry(id: number, request: EntryRequest): Observable<EntryResponse> {
    return this.update<EntryRequest, EntryResponse>(id, request);
  }

  /**
   * Delete an entry
   * 
   * @param id - Entry ID
   * @returns Observable of void (204 No Content) or 404 error
   * 
   * @example
   * deleteEntry(123).subscribe(() => {
   *   console.log('Entry deleted successfully');
   * })
   */
  deleteEntry(id: number): Observable<void> {
    return this.delete(id);
  }
}

