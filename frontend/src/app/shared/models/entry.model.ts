/**
 * Entry Models and DTOs
 * 
 * These interfaces match the backend DTOs for type safety
 */

/**
 * Entry Response DTO - matches backend EntryResponseDto
 */
export interface EntryResponse {
  id: number;
  key: string;
  value: string;
  createdAt: string; // ISO 8601 date string
  createdBy: string;
  updatedAt: string; // ISO 8601 date string
  updatedBy: string;
}

/**
 * Entry Request DTO - matches backend EntryRequestDto
 */
export interface EntryRequest {
  key: string;
  value: string;
}

/**
 * Paginated response for entries - matches Spring Data Page
 */
export interface PageResponse<T> {
  content: T[];
  pageable: {
    pageNumber: number;
    pageSize: number;
    sort: {
      sorted: boolean;
      unsorted: boolean;
      empty: boolean;
    };
    offset: number;
    paged: boolean;
    unpaged: boolean;
  };
  totalPages: number;
  totalElements: number;
  last: boolean;
  first: boolean;
  size: number;
  number: number;
  sort: {
    sorted: boolean;
    unsorted: boolean;
    empty: boolean;
  };
  numberOfElements: number;
  empty: boolean;
}

/**
 * Simplified page request parameters
 */
export interface PageRequest {
  page?: number;
  size?: number;
  sort?: string;
}

