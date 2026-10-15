import { IsNotEmpty, IsString, IsInt, IsOptional, IsBoolean, Min } from 'class-validator';
import { Type } from 'class-transformer';

export class UpsertProgressDto {
  @IsString({ message: 'contentId es requerido' })
  @IsNotEmpty({ message: 'contentId no puede estar vacío' })
  contentId!: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  partIndex: number = 0;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  @Min(0)
  episodeIndex: number = 0;

  @Type(() => Number)
  @IsInt()
  @Min(0)
  positionMs!: number;

  @Type(() => Number)
  @IsInt()
  @Min(0)
  durationMs!: number;

  @IsOptional()
  @IsBoolean()
  isCompleted?: boolean;
}
