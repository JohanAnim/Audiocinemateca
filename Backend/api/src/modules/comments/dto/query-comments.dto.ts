import { IsNotEmpty, IsString, IsInt, IsOptional, Min, Max } from 'class-validator';
import { Type } from 'class-transformer';

export class QueryCommentsDto {
  @IsString({ message: 'contentId es requerido' })
  @IsNotEmpty({ message: 'contentId no puede estar vacío' })
  contentId!: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  partIndex?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  episodeIndex?: number;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  page: number = 1;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(1)
  @Max(50)
  limit: number = 20;
}
