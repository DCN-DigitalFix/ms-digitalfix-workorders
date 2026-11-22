package cl.duoc.ms_digitalfix_workorders.exception;

/** Catalog no respondio (no es un rechazo de negocio, sino que no contesto). */
public class CatalogNoDisponibleException extends RuntimeException {
    public CatalogNoDisponibleException(String message) {
        super(message);
    }
}
