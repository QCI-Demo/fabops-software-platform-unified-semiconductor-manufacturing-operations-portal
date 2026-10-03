{% macro log_transformation_metadata(model_ref, model_name) %}
/*
    Logs transformation metadata to the data lineage catalog table.
    Used for tracking data lineage and transformation history.
    
    This macro is called as a post-hook after model materialization.
*/
{% if execute %}
    {% set query %}
        merge into {{ target.database }}.METADATA.TRANSFORMATION_LINEAGE as target
        using (
            select
                '{{ invocation_id }}' as invocation_id,
                '{{ model_name }}' as model_name,
                '{{ model_ref.schema }}.{{ model_ref.identifier }}' as full_table_name,
                '{{ target.schema }}' as target_schema,
                '{{ run_started_at }}' as run_started_at,
                current_timestamp() as completed_at,
                '{{ flags.WHICH }}' as dbt_command,
                '{{ target.name }}' as target_environment,
                parse_json('{{ tojson(graph.nodes[model_ref.unique_id].depends_on.nodes) }}') as upstream_models,
                '{{ env_var("DBT_CLOUD_RUN_ID", "local") }}' as run_id,
                '{{ env_var("DBT_CLOUD_JOB_ID", "local") }}' as job_id
        ) as source
        on target.invocation_id = source.invocation_id
           and target.model_name = source.model_name
        when matched then update set
            completed_at = source.completed_at
        when not matched then insert (
            invocation_id, model_name, full_table_name, target_schema,
            run_started_at, completed_at, dbt_command, target_environment,
            upstream_models, run_id, job_id
        ) values (
            source.invocation_id, source.model_name, source.full_table_name, source.target_schema,
            source.run_started_at, source.completed_at, source.dbt_command, source.target_environment,
            source.upstream_models, source.run_id, source.job_id
        );
    {% endset %}
    
    {% do run_query(query) %}
{% endif %}
{% endmacro %}
