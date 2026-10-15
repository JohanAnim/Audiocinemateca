export type ContentType = 'MOVIE' | 'SERIES' | 'DOCUMENTARY' | 'SHORT';
export type ContentStatus = 'PUBLISHED' | 'DRAFT' | 'ARCHIVED';

export interface EpisodeDto {
  episodeNumber: number;
  title: string;
  synopsis?: string;
  audioUrl: string;
  durationMinutes?: number;
}

export interface SeasonDto {
  seasonNumber: number;
  title?: string;
  overview?: string;
  episodes: EpisodeDto[];
}

export interface ContentItem {
  id: string;
  externalId?: string;
  title: string;
  slug: string;
  synopsis: string;
  aiSynopsis?: string;
  coverUrl?: string;
  bannerUrl?: string;
  type: ContentType;
  status: ContentStatus;
  releaseYear?: number;
  duration?: string;
  durationMinutes?: number;
  country?: string;
  director?: string;
  screenplay?: string;
  music?: string;
  cinematography?: string;
  cast?: string;
  production?: string;
  narrator?: string;
  audioDescription: boolean;
  dubbing?: string;
  language?: string;
  isExclusive: boolean;
  genreRaw?: string;
  filmaffinityUrl?: string;
  audioUrl?: string;
  audioUrls: string[];
  averageRating: number;
  ratingsCount: number;
  viewsCount: number;
  categories: { id: string; name: string; slug: string }[];
  seasons?: SeasonDto[];
  createdAt: string;
  updatedAt: string;
}

export interface CreateContentDto {
  externalId?: string;
  title: string;
  synopsis: string;
  coverUrl?: string;
  bannerUrl?: string;
  type?: ContentType;
  status?: ContentStatus;
  releaseYear?: number;
  duration?: string;
  durationMinutes?: number;
  country?: string;
  director?: string;
  screenplay?: string;
  music?: string;
  cinematography?: string;
  cast?: string;
  production?: string;
  narrator?: string;
  audioDescription?: boolean;
  dubbing?: string;
  language?: string;
  isExclusive?: boolean;
  genreRaw?: string;
  filmaffinityUrl?: string;
  audioUrl?: string;
  audioUrls?: string[];
  categories?: string[];
  seasons?: SeasonDto[];
}

export interface ContentsPaginatedResponse {
  data: ContentItem[];
  meta: {
    total: number;
    page: number;
    limit: number;
    totalPages: number;
  };
}
