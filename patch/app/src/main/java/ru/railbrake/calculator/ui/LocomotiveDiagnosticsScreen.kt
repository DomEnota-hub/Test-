package ru.railbrake.calculator.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.railbrake.calculator.core.TechnicalFamily
import ru.railbrake.calculator.core.TechnicalSection

@Composable
fun LocomotiveDiagnosticsScreen(initialScenarioId:String?=null,initialEquipmentId:String?=null){
    val initialFamily=if(initialScenarioId?.startsWith("ER-DIAG-")==true||initialEquipmentId?.startsWith("ER-EQ-")==true) TechnicalFamily.ERMAK else TechnicalFamily.VL80S
    var familyName by rememberSaveable { mutableStateOf(initialFamily.name) }
    val family=runCatching{TechnicalFamily.valueOf(familyName)}.getOrDefault(TechnicalFamily.VL80S)
    Column(Modifier.fillMaxSize()){
        Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=8.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            FilterChip(family==TechnicalFamily.VL80S,{familyName=TechnicalFamily.VL80S.name},label={Text("ВЛ80С")})
            FilterChip(family==TechnicalFamily.ERMAK,{familyName=TechnicalFamily.ERMAK.name},label={Text("Ермак")})
            Text("Алгоритмы разделены по серии",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Bold,modifier=Modifier.padding(top=10.dp))
        }
        Box(Modifier.fillMaxWidth().weight(1f)){
            if(family==TechnicalFamily.VL80S) DiagnosticScreen(initialScenarioId?.takeUnless{it.startsWith("ER-")},initialEquipmentId?.takeUnless{it.startsWith("ER-")})
            else TechnicalCatalogScreen(
                initialFamily=TechnicalFamily.ERMAK,initialSection=TechnicalSection.DIAGNOSTICS,
                sectionBackLabel="Диагностика ВЛ80С",onSectionBack={familyName=TechnicalFamily.VL80S.name},
                lockFamily=true,lockSection=true,
                initialEntryId=initialScenarioId?.takeIf{it.startsWith("ER-DIAG-")}?:initialEquipmentId?.takeIf{it.startsWith("ER-EQ-")}
            )
        }
    }
}
