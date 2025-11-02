import { inject } from '@angular/core';
import { HttpClient, HttpParams, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';

/**
 * Base API Service
 * 
 * Provides common HTTP operations with:
 * - Type safety
 * - Consistent error handling
 * - Centralized API URL configuration
 * - Reusable CRUD operations
 * 
 * Usage:
 * ```typescript
 * @Injectable({ providedIn: 'root' })
 * export class MyApiService extends BaseApiService {
 *   constructor() {
 *     super('/api/v1/my-resource');
 *   }
 * }
 * ```
 */
export abstract class BaseApiService {
  protected readonly http = inject(HttpClient);
  protected readonly baseUrl = 'http://localhost:8080'; // Gateway URL
  protected readonly resourceUrl: string;

  /**
   * @param resourcePath - API resource path (e.g., '/api/v1/entries')
   */
  constructor(resourcePath: string) {
    this.resourceUrl = `${this.baseUrl}${resourcePath}`;
  }

  /**
   * GET request to fetch a single resource by ID
   * 
   * @param id - Resource ID
   * @returns Observable of the resource
   */
  protected getById<T>(id: number | string): Observable<T> {
    return this.http.get<T>(`${this.resourceUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  /**
   * GET request to fetch all resources (with optional query params)
   * 
   * @param params - Optional query parameters
   * @returns Observable of the resource array or paginated response
   */
  protected getAll<T>(params?: Record<string, any>): Observable<T> {
    const httpParams = this.buildHttpParams(params);
    return this.http.get<T>(this.resourceUrl, { params: httpParams })
      .pipe(catchError(this.handleError));
  }

  /**
   * POST request to create a new resource
   * 
   * @param data - Resource data
   * @returns Observable of the created resource
   */
  protected create<T, R>(data: T): Observable<R> {
    return this.http.post<R>(this.resourceUrl, data)
      .pipe(catchError(this.handleError));
  }

  /**
   * PUT request to update an existing resource
   * 
   * @param id - Resource ID
   * @param data - Updated resource data
   * @returns Observable of the updated resource
   */
  protected update<T, R>(id: number | string, data: T): Observable<R> {
    return this.http.put<R>(`${this.resourceUrl}/${id}`, data)
      .pipe(catchError(this.handleError));
  }

  /**
   * DELETE request to remove a resource
   * 
   * @param id - Resource ID
   * @returns Observable of void
   */
  protected delete(id: number | string): Observable<void> {
    return this.http.delete<void>(`${this.resourceUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  /**
   * Build HttpParams from object
   * 
   * @param params - Parameters object
   * @returns HttpParams
   */
  protected buildHttpParams(params?: Record<string, any>): HttpParams {
    let httpParams = new HttpParams();
    
    if (params) {
      Object.keys(params).forEach(key => {
        const value = params[key];
        if (value !== null && value !== undefined) {
          httpParams = httpParams.set(key, value.toString());
        }
      });
    }
    
    return httpParams;
  }

  /**
   * Centralized error handling
   * 
   * @param error - HTTP error response
   * @returns Observable that throws the error
   */
  protected handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An unknown error occurred';
    
    if (error.error instanceof ErrorEvent) {
      // Client-side or network error
      errorMessage = `Client Error: ${error.error.message}`;
    } else {
      // Backend error
      errorMessage = error.error?.message || 
                     error.message || 
                     `Server Error: ${error.status} - ${error.statusText}`;
    }
    
    console.error('API Error:', errorMessage, error);
    
    return throwError(() => ({
      status: error.status,
      message: errorMessage,
      error: error.error
    }));
  }
}

