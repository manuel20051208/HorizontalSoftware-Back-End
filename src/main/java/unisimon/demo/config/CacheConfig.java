package unisimon.demo.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.cache.transaction.TransactionAwareCacheManagerProxy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import unisimon.demo.cache.NombresCache;

/**
 * Cache de Spring en memoria (ConcurrentMap), con los caches declarados en {@link NombresCache}.
 * <p>
 * Va envuelto en un proxy transaccional: lo que se guarda o se invalida dentro de una transaccion se aplica
 * solo cuando esta confirma (COMMIT). Asi nadie vuelve a llenar el cache con datos viejos mientras la escritura
 * sigue abierta, y un ROLLBACK no invalida nada.
 * <p>
 * Para pasar a Redis: reemplazar este bean por un RedisCacheManager con {@code transactionAware()}.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        return new TransactionAwareCacheManagerProxy(new ConcurrentMapCacheManager(NombresCache.TODOS));
    }
}
