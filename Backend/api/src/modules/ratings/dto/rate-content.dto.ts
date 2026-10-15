import { IsNotEmpty, IsString, IsInt, Min, Max, IsOptional } from 'class-validator';
import { Type } from 'class-transformer';

export class RateContentDto {
  @IsString({ message: 'contentId es requerido' })
  @IsNotEmpty({ message: 'contentId no puede estar vacío' })
  contentId!: string;

  @Type(() => Number)
  @IsInt({ message: 'La calificación debe ser un número entero' })
  @Min(1, { message: 'La calificación mínima es 1' })
  @Max(5, { message: 'La calificación máxima es 5' })
  score!: number;

  @IsOptional()
  @IsString()
  review?: string;
}
