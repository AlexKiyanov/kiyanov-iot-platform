FROM cassandra:latest

# Устанавливаем необходимые инструменты
RUN apt-get update && apt-get install -y \
    curl \
    && rm -rf /var/lib/apt/lists/*

# Копируем скрипт инициализации
COPY cassandra-init.sh /usr/local/bin/
COPY schema.cql /schema/

# Делаем скрипт исполняемым
RUN chmod +x /usr/local/bin/cassandra-init.sh

# Устанавливаем точку входа
ENTRYPOINT ["/usr/local/bin/cassandra-init.sh"]
