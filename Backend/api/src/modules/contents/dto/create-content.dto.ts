import {
  IsEnum,
  IsInt,
  IsNotEmpty,
  IsOptional,
  IsString,
  IsArray,
  IsBoolean,
  ValidateNested,
} from 'class-validator';
import { Type } from 'class-transformer';
import { ContentType, ContentStatus } from '@prisma/client';

export class CreateEpisodeDto {
  @IsInt()
  episodeNumber!: number;

  @IsString()
  @IsNotEmpty()
  title!: string;

  @IsOptional()
  @IsString()
  synopsis?: string;

  @IsString()
  @IsNotEmpty()
  audioUrl!: string;

  @IsOptional()
  @IsInt()
  durationMinutes?: number;
}

export class CreateSeasonDto {
  @IsInt()
  seasonNumber!: number;

  @IsOptional()
  @IsString()
  title?: string;

  @IsOptional()
  @IsString()
  overview?: string;

  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateEpisodeDto)
  episodes!: CreateEpisodeDto[];
}

export class CreateContentDto {
  @IsOptional()
  @IsString()
  externalId?: string;

  @IsString()
  @IsNotEmpty()
  title!: string;

  @IsString()
  @IsNotEmpty()
  synopsis!: string;

  @IsOptional()
  @IsString()
  coverUrl?: string;

  @IsOptional()
  @IsString()
  bannerUrl?: string;

  @IsOptional()
  @IsEnum(ContentType)
  type?: ContentType = ContentType.MOVIE;

  @IsOptional()
  @IsEnum(ContentStatus)
  status?: ContentStatus = ContentStatus.PUBLISHED;

  @IsOptional()
  @IsInt()
  releaseYear?: number;

  @IsOptional()
  @IsString()
  duration?: string;

  @IsOptional()
  @IsInt()
  durationMinutes?: number;

  @IsOptional()
  @IsString()
  country?: string;

  @IsOptional()
  @IsString()
  director?: string;

  @IsOptional()
  @IsString()
  screenplay?: string;

  @IsOptional()
  @IsString()
  music?: string;

  @IsOptional()
  @IsString()
  cinematography?: string;

  @IsOptional()
  @IsString()
  cast?: string;

  @IsOptional()
  @IsString()
  production?: string;

  @IsOptional()
  @IsString()
  narrator?: string;

  @IsOptional()
  @IsBoolean()
  audioDescription?: boolean = true;

  @IsOptional()
  @IsString()
  dubbing?: string;

  @IsOptional()
  @IsString()
  genreRaw?: string;

  @IsOptional()
  @IsString()
  filmaffinityUrl?: string;

  @IsOptional()
  @IsString()
  audioUrl?: string;

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  audioUrls?: string[];

  @IsOptional()
  @IsArray()
  @IsString({ each: true })
  categories?: string[];

  @IsOptional()
  @IsArray()
  @ValidateNested({ each: true })
  @Type(() => CreateSeasonDto)
  seasons?: CreateSeasonDto[];
}
