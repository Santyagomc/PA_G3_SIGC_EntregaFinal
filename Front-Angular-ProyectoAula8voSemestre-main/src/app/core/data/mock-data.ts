import type { ChatMessage, DashboardStats, UiDocument, UiRequest, UiUser } from '../models/pqrs.models';

export const MOCK_USERS: UiUser[] = [
  {
    id: 1,
    created_at: '2024-01-15T10:30:00',
    document: '1075312456',
    email: 'admin@huila.gov.co',
    failed_attempts: 0,
    full_name: 'Carlos Andrés Martínez',
    lock_until: null,
    rol: 'ADMIN',
    status: 'ACTIVO',
    updated_at: '2024-03-20T15:45:00',
  },
  {
    id: 2,
    created_at: '2024-02-10T08:00:00',
    document: '1075456789',
    email: 'funcionario@huila.gov.co',
    failed_attempts: 0,
    full_name: 'María Elena Rodríguez',
    lock_until: null,
    rol: 'FUNCIONARIO',
    status: 'ACTIVO',
    updated_at: '2024-03-18T12:00:00',
  },
  {
    id: 3,
    created_at: '2024-02-25T14:20:00',
    document: '1075789123',
    email: 'ciudadano1@gmail.com',
    failed_attempts: 0,
    full_name: 'Juan Pablo López García',
    lock_until: null,
    rol: 'CIUDADANO',
    status: 'ACTIVO',
    updated_at: '2024-03-15T09:30:00',
  },
  {
    id: 4,
    created_at: '2024-03-01T11:00:00',
    document: '1075321654',
    email: 'ciudadano2@hotmail.com',
    failed_attempts: 3,
    full_name: 'Ana María Sánchez Torres',
    lock_until: '2024-03-25T12:00:00',
    rol: 'CIUDADANO',
    status: 'BLOQUEADO',
    updated_at: '2024-03-22T10:15:00',
  },
  {
    id: 5,
    created_at: '2024-03-05T09:45:00',
    document: '1075654987',
    email: 'pedro.hernandez@email.com',
    failed_attempts: 0,
    full_name: 'Pedro José Hernández',
    lock_until: null,
    rol: 'CIUDADANO',
    status: 'ACTIVO',
    updated_at: '2024-03-20T16:00:00',
  },
  {
    id: 6,
    created_at: '2024-03-08T16:30:00',
    document: '1075987321',
    email: 'lucia.gomez@email.com',
    failed_attempts: 0,
    full_name: 'Lucía Fernanda Gómez',
    lock_until: null,
    rol: 'FUNCIONARIO',
    status: 'INACTIVO',
    updated_at: '2024-03-19T11:20:00',
  },
];

export const MOCK_REQUESTS: UiRequest[] = [
  {
    id: 1,
    city: 'Neiva',
    created_at: '2024-03-15T10:00:00',
    description:
      'Solicito información sobre el proceso de inscripción al programa de subsidio de vivienda para familias de estratos 1 y 2 en el municipio de Neiva.',
    document: '1075789123',
    email: 'ciudadano1@gmail.com',
    full_name: 'Juan Pablo López García',
    phone: '3201234567',
    status: 'PENDIENTE',
    title: 'Información subsidio de vivienda',
    type: 'PETICIÓN',
  },
  {
    id: 2,
    city: 'Pitalito',
    created_at: '2024-03-14T14:30:00',
    description:
      'Reclamo por el mal estado de las vías principales del barrio Las Américas, específicamente en la calle 5 entre carreras 8 y 10. Los huecos están causando accidentes.',
    document: '1075321654',
    email: 'ciudadano2@hotmail.com',
    full_name: 'Ana María Sánchez Torres',
    phone: '3159876543',
    status: 'CREADO',
    title: 'Mal estado de vías en barrio Las Américas',
    type: 'RECLAMO',
  },
  {
    id: 3,
    city: 'Garzón',
    created_at: '2024-03-13T09:15:00',
    description:
      'Queja por la demora en la atención en la oficina de impuestos del municipio. Llevo más de 3 horas esperando y no me atienden.',
    document: '1075654987',
    email: 'pedro.hernandez@email.com',
    full_name: 'Pedro José Hernández',
    phone: '3114567890',
    status: 'CERRADO',
    title: 'Demora en atención oficina de impuestos',
    type: 'QUEJA',
  },
  {
    id: 4,
    city: 'Neiva',
    created_at: '2024-03-12T11:45:00',
    description: 'Petición de copia de mi certificado de tradición del predio ubicado en la vereda San Antonio.',
    document: '1075789123',
    email: 'ciudadano1@gmail.com',
    full_name: 'Juan Pablo López García',
    phone: '3201234567',
    status: 'CERRADO',
    title: 'Copia certificado de tradición',
    type: 'PETICIÓN',
  },
  {
    id: 5,
    city: 'La Plata',
    created_at: '2024-03-11T16:00:00',
    description:
      'Reclamo porque el acueducto no está llegando con presión suficiente a mi domicilio desde hace 2 semanas.',
    document: '1075654987',
    email: 'pedro.hernandez@email.com',
    full_name: 'Pedro José Hernández',
    phone: '3114567890',
    status: 'PENDIENTE',
    title: 'Baja presión del acueducto',
    type: 'RECLAMO',
  },
  {
    id: 6,
    city: 'Campoalegre',
    created_at: '2024-03-10T08:30:00',
    description:
      'Queja por el comportamiento inapropiado de un funcionario en la ventanilla de atención al ciudadano.',
    document: '1075321654',
    email: 'ciudadano2@hotmail.com',
    full_name: 'Ana María Sánchez Torres',
    phone: '3159876543',
    status: 'PENDIENTE',
    title: 'Comportamiento inadecuado de funcionario',
    type: 'QUEJA',
  },
  {
    id: 7,
    city: 'Neiva',
    created_at: '2024-03-09T13:20:00',
    description:
      'Solicito información sobre los requisitos para obtener el certificado de estratificación socioeconómica.',
    document: '1075987321',
    email: 'lucia.gomez@email.com',
    full_name: 'Lucía Fernanda Gómez',
    phone: '3187654321',
    status: 'CREADO',
    title: 'Requisitos certificado de estratificación',
    type: 'PETICIÓN',
  },
  {
    id: 8,
    city: 'Rivera',
    created_at: '2024-03-08T10:10:00',
    description:
      'Reclamo por el cobro excesivo en el impuesto predial de este año. El valor aumentó un 40% sin justificación.',
    document: '1075789123',
    email: 'ciudadano1@gmail.com',
    full_name: 'Juan Pablo López García',
    phone: '3201234567',
    status: 'PENDIENTE',
    title: 'Cobro excesivo impuesto predial',
    type: 'RECLAMO',
  },
];

export const MOCK_DOCUMENTS: UiDocument[] = [
  {
    id: 1,
    created_at: '2024-03-15T10:05:00',
    file_name: 'cedula_juan_lopez.pdf',
    file_path: '/uploads/docs/cedula_juan_lopez.pdf',
    type: 'IDENTIFICACION',
    request_id: 1,
  },
  {
    id: 2,
    created_at: '2024-03-14T14:35:00',
    file_name: 'fotos_vias_danadas.jpg',
    file_path: '/uploads/docs/fotos_vias_danadas.jpg',
    type: 'EVIDENCIA',
    request_id: 2,
  },
  {
    id: 3,
    created_at: '2024-03-13T09:20:00',
    file_name: 'turno_atencion.pdf',
    file_path: '/uploads/docs/turno_atencion.pdf',
    type: 'SOPORTE',
    request_id: 3,
  },
  {
    id: 4,
    created_at: '2024-03-13T15:30:00',
    file_name: 'respuesta_oficina_impuestos.pdf',
    file_path: '/uploads/docs/respuesta_oficina_impuestos.pdf',
    type: 'RESPUESTA_ENTIDAD',
    request_id: 3,
  },
  {
    id: 5,
    created_at: '2024-03-12T11:50:00',
    file_name: 'escritura_predio.pdf',
    file_path: '/uploads/docs/escritura_predio.pdf',
    type: 'CONTRATO',
    request_id: 4,
  },
  {
    id: 6,
    created_at: '2024-03-11T16:05:00',
    file_name: 'factura_acueducto.pdf',
    file_path: '/uploads/docs/factura_acueducto.pdf',
    type: 'FACTURA',
    request_id: 5,
  },
  {
    id: 7,
    created_at: '2024-03-08T10:15:00',
    file_name: 'recibo_impuesto_2023.pdf',
    file_path: '/uploads/docs/recibo_impuesto_2023.pdf',
    type: 'FACTURA',
    request_id: 8,
  },
  {
    id: 8,
    created_at: '2024-03-08T10:16:00',
    file_name: 'recibo_impuesto_2024.pdf',
    file_path: '/uploads/docs/recibo_impuesto_2024.pdf',
    type: 'FACTURA',
    request_id: 8,
  },
];

export const MOCK_DASHBOARD_STATS: DashboardStats = {
  totalRequests: 8,
  pendingRequests: 4,
  closedRequests: 2,
  activeUsers: 4,
  requestsByType: { peticiones: 3, quejas: 2, reclamos: 3 },
};

export const CHATBOT_RESPONSES: Record<string, string> = {
  peticion: `Para radicar una **PETICIÓN** debe tener en cuenta:

📋 **Documentos requeridos:**
- Copia de cédula de ciudadanía
- Formulario de petición diligenciado
- Documentos de soporte según el caso

⏰ **Tiempo de respuesta:** 15 días hábiles

💡 **Ejemplos de peticiones:**
- Solicitud de información
- Copia de documentos
- Certificaciones`,

  queja: `Para radicar una **QUEJA** debe tener en cuenta:

📋 **Documentos requeridos:**
- Copia de cédula de ciudadanía
- Descripción detallada de los hechos
- Evidencias (fotos, videos, testimonios)

⏰ **Tiempo de respuesta:** 15 días hábiles

💡 **Ejemplos de quejas:**
- Mal servicio de funcionarios
- Instalaciones en mal estado
- Incumplimiento de horarios`,

  reclamo: `Para radicar un **RECLAMO** debe tener en cuenta:

📋 **Documentos requeridos:**
- Copia de cédula de ciudadanía
- Documento que sustente el reclamo (facturas, recibos)
- Evidencias del perjuicio

⏰ **Tiempo de respuesta:** 15 días hábiles

💡 **Ejemplos de reclamos:**
- Cobros indebidos
- Daños en infraestructura
- Fallas en servicios públicos`,

  default: `¡Hola! Soy el asistente virtual de PQRS del Huila. 🤖

Puedo ayudarte a orientar tu solicitud. ¿Qué tipo de trámite deseas realizar?

1️⃣ **Petición** - Solicitar información o documentos
2️⃣ **Queja** - Reportar inconformidad con el servicio
3️⃣ **Reclamo** - Exigir la solución de un problema

Escribe el número o el tipo de solicitud para más información.`,

  documento: `Sobre **documentos** en PQRS:

📎 **Suele pedirse:** cédula, formularios de la entidad, soportes en PDF o imagen según el trámite.

❓ **Puede contarme:** qué trámite quiere hacer o qué archivo tiene (por ejemplo «tengo una factura en PDF»).

📤 **Subir archivo:** use el área «Adjuntar archivo»; así sabré el nombre y tipo y le oriento si sirve como evidencia, soporte o identificación.

🔐 **Radicación oficial:** para vincular archivos a una solicitud ya creada, use el sistema con **Iniciar sesión**.`,

  subir: `Para **subir o asociar documentos** de forma oficial:

1. **Aquí en el asistente** puede adjuntar un archivo solo para **orientación** (le decimos si encaja como soporte típico).
2. **En el sistema autenticado**, en el módulo **Documentos**, podrá subirlos a una solicitud concreta (requiere cuenta).

Formatos habituales: **PDF, JPG, PNG** (tamaño según política del servidor). Si ya eligió un archivo abajo, escriba qué trámite relaciona con él.`,
};

export function initialChatMessages(): ChatMessage[] {
  return [
    {
      id: '1',
      role: 'assistant',
      content: CHATBOT_RESPONSES['default'],
      timestamp: new Date(),
    },
  ];
}

/** Mensaje inicial en la ruta pública /asistente (sin login). */
export function initialPublicChatMessages(): ChatMessage[] {
  return [
    {
      id: '1',
      role: 'assistant',
      content: `¡Hola! Soy el **asistente público** de SIGC PQRS (Huila). 👋

Puede **escribir con libertad**: pregunte por un trámite, por qué documentos necesita o describa su caso.

También puede **adjuntar un archivo** (PDF, imagen, etc.) con el botón de abajo; le orientaré según el nombre y tipo de archivo.

Si luego desea **radicar de forma oficial**, use **Registrarse** o **Ingresar**.`,
      timestamp: new Date(),
    },
  ];
}
