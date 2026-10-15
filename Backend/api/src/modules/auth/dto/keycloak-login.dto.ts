import { IsNotEmpty, IsString } from 'class-validator';

export class KeycloakLoginDto {
  @IsString({ message: 'El token de Keycloak debe ser una cadena de texto' })
  @IsNotEmpty({ message: 'El token de Keycloak es requerido' })
  token!: string;
}
