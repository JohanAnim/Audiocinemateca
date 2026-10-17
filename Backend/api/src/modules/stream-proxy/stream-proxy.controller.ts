import { Controller, Get, Head, Query, Req, Res } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import { Public } from '../auth/decorators/public.decorator.js';
import type { Request, Response } from 'express';
import * as https from 'https';
import * as http from 'http';

@Controller()
export class StreamProxyController {
  constructor(private readonly configService: ConfigService) {}

  @Public()
  @Get(['stream/proxy', 'api/v1/stream/proxy'])
  async proxyGet(
    @Query('url') targetUrl: string,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    return this.handleProxy(targetUrl, 'GET', req, res);
  }

  @Public()
  @Head(['stream/proxy', 'api/v1/stream/proxy'])
  async proxyHead(
    @Query('url') targetUrl: string,
    @Req() req: Request,
    @Res() res: Response,
  ) {
    return this.handleProxy(targetUrl, 'HEAD', req, res);
  }

  private handleProxy(
    targetUrl: string,
    method: 'GET' | 'HEAD',
    req: Request,
    res: Response,
  ) {
    if (!targetUrl) {
      res.status(400).json({ error: 'URL requerida' });
      return;
    }

    try {
      let fullTarget = targetUrl.trim();
      if (!fullTarget.startsWith('http://') && !fullTarget.startsWith('https://')) {
        fullTarget = 'https://audiocinemateca.com/' + fullTarget.replace(/^\/+/, '');
      }

      const targetObj = new URL(fullTarget);
      const isHttps = targetObj.protocol === 'https:';
      const httpModule = isHttps ? https : http;

      const headers: Record<string, string> = {};
      if (req.headers.range) {
        headers.range = String(req.headers.range);
      }

      const user = (this.configService.get<string>('CATALOG_USER') || 'Johan-a-g').replace(/^["']|["']$/g, '');
      const pass = (this.configService.get<string>('CATALOG_PASS') || '3tmgs8qd').replace(/^["']|["']$/g, '');
      headers.Authorization = 'Basic ' + Buffer.from(`${user}:${pass}`).toString('base64');
      headers['User-Agent'] = 'Audiocinemateca-CastProxy/3.1';

      const proxyReq = httpModule.request(
        fullTarget,
        {
          method,
          headers,
        },
        (proxyRes) => {
          res.setHeader('Access-Control-Allow-Origin', '*');
          res.setHeader('Access-Control-Allow-Methods', 'GET, HEAD, OPTIONS');
          res.setHeader('Accept-Ranges', proxyRes.headers['accept-ranges'] || 'bytes');

          if (proxyRes.headers['content-type']) {
            res.setHeader('Content-Type', proxyRes.headers['content-type']);
          }
          if (proxyRes.headers['content-length']) {
            res.setHeader('Content-Length', proxyRes.headers['content-length']);
          }
          if (proxyRes.headers['content-range']) {
            res.setHeader('Content-Range', proxyRes.headers['content-range']);
          }

          res.status(proxyRes.statusCode || 200);
          if (method === 'HEAD') {
            res.end();
          } else {
            proxyRes.pipe(res);
          }
        },
      );

      proxyReq.on('error', () => {
        if (!res.headersSent) {
          res.status(500).json({ error: 'Error al canalizar el stream de audio' });
        }
      });

      proxyReq.end();
    } catch {
      res.status(400).json({ error: 'URL no válida' });
    }
  }
}
