package cl.duoc.ms_digitalfix_workorders.exception;

/** El repuesto existe pero catalog no tiene stock suficiente para la cantidad pedida. */
public class StockInsuficienteException extends RuntimeException {
    public StockInsuficienteException(String message) {
        super(message);
    }
}
