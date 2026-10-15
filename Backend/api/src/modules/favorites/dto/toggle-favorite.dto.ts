import { IsNotEmpty, IsString } from 'class-validator';

export class ToggleFavoriteDto {
  @IsString({ message: 'contentId es requerido' })
  @IsNotEmpty({ message: 'contentId no puede estar vacío' })
  contentId!: string;
}
