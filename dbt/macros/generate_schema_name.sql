{% macro generate_schema_name(custom_schema_name, node) %}
    {#
        Override default schema naming to use custom schema directly.
        In production, models go to their specified schema (staging, kpi).
        In dev, they go to dev_<user>_<schema> for isolation.
    #}
    {% if target.name == 'prod' %}
        {{ custom_schema_name | default(target.schema, true) | trim }}
    {% else %}
        {{ target.schema }}_{{ custom_schema_name | default('default', true) | trim }}
    {% endif %}
{% endmacro %}
