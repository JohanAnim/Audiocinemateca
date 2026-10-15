import { IsNotEmpty, IsString } from 'class-validator';

export class GoogleLoginDto {
  @IsString({ message: 'El idToken de Google debe ser una cadena de texto' })
  @IsNotEmpty({ message: 'El idToken de Google es requerido' })
  idToken!: string;
}
