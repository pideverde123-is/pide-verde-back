package com.pideeverde.pideverde;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.bind.annotation.CrossOrigin;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Arrays;
import java.util.List;

@RestController
@RequestMapping("/api/leads")
@CrossOrigin(origins = "*") 
public class LeadController {

    @Autowired
    private LeadRepository leadRepository;

    // 1. Lista blanca de Facultades (Nadie podrá ingresar una distinta a estas)
    private final List<String> FACULTADES_PERMITIDAS = Arrays.asList(
        // Área de Ciencias Naturales y Exactas
        "Facultad de Ciencias",
        "Facultad de Química",
        "Facultad de Medicina",
        "Facultad de Medicina Veterinaria y Zootecnia",
        "Facultad de Odontología",
        "Facultad de Enfermería y Obstetricia",
        
        // Área de Ingeniería y Tecnología
        "Facultad de Ingeniería",
        "Facultad de Arquitectura y Diseño",
        "Facultad de Ingeniería en Sistemas",
        
        // Área de Ciencias Sociales y Administrativas
        "Facultad de Ciencias Políticas y Sociales",
        "Facultad de Contaduría y Administración",
        "Facultad de Derecho",
        "Facultad de Economía",
        "Facultad de Turismo y Gastronomía",
        "Facultad de Trabajo Social y Humanidades",
        
        // Área de Humanidades y Artes
        "Facultad de Humanidades",
        "Facultad de Lenguas",
        "Facultad de Artes",
        "Facultad de Ciencias de la Conducta",
        "Facultad de Geografía",
        
        // Unidades Académicas Profesionales
        "UAP Atlacomulco",
        "UAP Chimalhuacán",
        "UAP Ecatepec",
        "UAP Nezahualcóyotl",
        "UAP Temascaltepec",
        "UAP Texcoco",
        "UAP Valle de Chalco",
        "UAP Valle de México",
        "UAP Valle de Teotihuacán",
        "UAP Zumpango"
    );

    @PostMapping
    public ResponseEntity<String> registrarLead(@Valid @RequestBody Lead lead) {
        
        // VALIDACIÓN 1: Dominio de correo
        if (!lead.getCorreo().endsWith("@uaemex.mx") && !lead.getCorreo().endsWith("@alumno.uaemex.mx")) {
            return ResponseEntity.badRequest().body("{\"message\": \"Dominio no válido\"}");
        }

        // VALIDACIÓN 2: Nombre solo letras y espacios (Regex)
        if (!lead.getNombre().matches("^[a-zA-ZáéíóúÁÉÍÓÚñÑ\\s]+$")) {
            return ResponseEntity.badRequest().body("{\"message\": \"El nombre solo debe contener letras\"}");
        }

        // VALIDACIÓN 3: Facultad estricta
        if (!FACULTADES_PERMITIDAS.contains(lead.getFacultad())) {
            return ResponseEntity.badRequest().body("{\"message\": \"Facultad no reconocida o alterada\"}");
        }
        
        try {
            // FLUJO ORDENADO: Primero guardamos y esperamos confirmación de Clever Cloud
            Lead leadGuardado = leadRepository.save(lead);
            
            // Segundo: HubSpot jala exactamente los datos que devolvió la base de datos
            notificarCRM(leadGuardado);
            
            // Tercero: Se dispara el correo de SendGrid
            notificarVentas(leadGuardado);
            
            return ResponseEntity.ok("{\"message\": \"Guardado correctamente\"}");
            
        } catch (DataIntegrityViolationException e) { 
            return ResponseEntity.badRequest().body("{\"message\": \"El correo ingresado ya se encuentra registrado.\"}");
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("{\"message\": \"Error en servidor de base de datos\"}");
        }
    }

    private void notificarCRM(Lead lead) {
        try {
            String token = System.getenv("HUBSPOT_TOKEN"); 
            // CAMBIO CLAVE PARA HUBSPOT: Usamos una propiedad personalizada llamada "facultad" en lugar de "company"
            String json = String.format("{\"properties\": {\"firstname\": \"%s\", \"email\": \"%s\", \"facultad\": \"%s\"}}", 
                                        lead.getNombre(), lead.getCorreo(), lead.getFacultad());
            
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.hubapi.com/crm/v3/objects/contacts"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(json)).build();
            client.sendAsync(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception ignored) {}
    }
     
    private void notificarVentas(Lead lead) {
        try {
            String token = System.getenv("SENDGRID_TOKEN"); 
            HttpClient client = HttpClient.newHttpClient();
            String correoVerificado = "pideverde123@gmail.com"; 

            // CORREO 1: NOTIFICACIÓN INTERNA
            String jsonParaMi = String.format(
                "{\"personalizations\": [{\"to\": [{\"email\": \"pideverde123@gmail.com\"}]}],\"from\": {\"email\": \"%s\"},\"subject\": \"¡Nuevo Lead: %s!\",\"content\": [{\"type\": \"text/plain\", \"value\": \"Nombre: %s\\nCorreo: %s\\nFacultad: %s\"}]}", 
                correoVerificado, lead.getNombre(), lead.getNombre(), lead.getCorreo(), lead.getFacultad()
            );

            HttpRequest requestParaMi = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonParaMi)).build();
            
            // Usamos sendAsync para que la lentitud de SendGrid no congele la respuesta al usuario
            client.sendAsync(requestParaMi, HttpResponse.BodyHandlers.ofString());

            // CORREO 2: BIENVENIDA ALUMNO
            String htmlBody = "<div style='font-family: Arial, sans-serif; max-width: 600px; margin: 0 auto; border: 1px solid #e0e0e0; border-radius: 8px; overflow: hidden;'>"
                    + "  <div style='background-color: #2e7d32; padding: 20px; text-align: center;'>"
                    + "    <h1 style='color: white; margin: 0; font-size: 24px;'>¡Bienvenido a Pide Verde! 🥗</h1>"
                    + "  </div>"
                    + "  <div style='padding: 20px; color: #333333; line-height: 1.6;'>"
                    + "    <p style='font-size: 16px;'>Hola <b>" + lead.getNombre() + "</b>,</p>"
                    + "    <p>¡Muchas gracias por sumarte a la iniciativa! Tu registro para la comunidad de la <b>" + lead.getFacultad() + "</b> ha sido procesado con éxito.</p>"
                    + "    <div style='text-align: center; margin: 25px 0;'>"
                    + "      <img src='https://i.postimg.cc/63JMXKZ4/Whats-App-Image-2026-05-18-at-21-31-19.jpg' style='width: 100%; max-width: 500px; border-radius: 6px; box-shadow: 0 4px 6px rgba(0,0,0,0.1);'>"
                    + "    </div>"
                    + "    <p style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #2e7d32; font-style: italic; margin: 20px 0;'>"
                    + "      \"Tu espacio universitario, ahora más verde y saludable.\""
                    + "    </p>"
                    + "  </div>"
                    + "</div>";

            String jsonParaAlumno = String.format(
                "{\"personalizations\": [{\"to\": [{\"email\": \"%s\"}]}],\"from\": {\"email\": \"%s\"},\"subject\":\"¡Hola %s, tu registro en Pide Verde fue un éxito! 🎉\",\"content\": [{\"type\": \"text/html\", \"value\": \"%s\"}]}", 
                lead.getCorreo(), correoVerificado, lead.getNombre(), htmlBody.replace("\"", "\\\"")
            );

            HttpRequest requestParaAlumno = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.sendgrid.com/v3/mail/send"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + token)
                    .POST(HttpRequest.BodyPublishers.ofString(jsonParaAlumno)).build();

            // Usamos sendAsync para liberar el hilo de la base de datos lo más rápido posible
            client.sendAsync(requestParaAlumno, HttpResponse.BodyHandlers.ofString());

        } catch (Exception e) {
            e.printStackTrace(); 
        }       
    }
}