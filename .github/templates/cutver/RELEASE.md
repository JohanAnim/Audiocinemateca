{% if breaking %}
### Cambios Importantes
{{ breaking }}
{% endif %}

{% if features %}
### Nuevas Funciones
{{ features }}
{% endif %}

{% if fixes %}
### Correcciones
{{ fixes }}
{% endif %}

{% if perf %}
### Rendimiento
{{ perf }}
{% endif %}

{% if refactor %}
### Mejoras Internas
{{ refactor }}
{% endif %}

{% if docs %}
### Documentación
{{ docs }}
{% endif %}

{% if maintenance %}
### Mantenimiento
{{ maintenance }}
{% endif %}

{% if other %}
### Otros Cambios
{{ other }}
{% endif %}

{% if compare_url %}
**Comparación completa:** [Ver cambios]({{ compare_url }})
{% endif %}
