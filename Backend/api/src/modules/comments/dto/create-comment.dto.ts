import { IsNotEmpty, IsString, IsInt, IsOptional, MaxLength } from 'class-validator';
import { Type } from 'class-transformer';

export class CreateCommentDto {
  @IsString({ message: 'contentId es requerido' })
  @IsNotEmpty({ message: 'contentId no puede estar vacío' })
  contentId!: string;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  partIndex: number = 0;

  @IsOptional()
  @Type(() => Number)
  @IsInt()
  episodeIndex: number = 0;

  @IsString({ message: 'El texto del comentario es requerido' })
  @IsNotEmpty({ message: 'El comentario no puede estar vacío' })
  @MaxLength(2000, { message: 'El comentario no puede exceder los 2000 caracteres' })
  commentText!: string;
}
