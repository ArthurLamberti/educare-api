package br.com.educare.api.aluno

import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.support.ServletUriComponentsBuilder

@RestController
@RequestMapping("/api/alunos")
class AlunoController(private val service: AlunoService) {

    @GetMapping
    fun listar(@PageableDefault(size = 20, sort = ["nome"]) pageable: Pageable): Page<AlunoResponse> =
        service.listar(pageable)

    @GetMapping("/{id}")
    fun buscar(@PathVariable id: Long): AlunoResponse = service.buscar(id)

    @PostMapping
    fun criar(@Valid @RequestBody request: AlunoRequest): ResponseEntity<AlunoResponse> {
        val criado = service.criar(request)
        val location = ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}").buildAndExpand(criado.id).toUri()
        return ResponseEntity.created(location).body(criado)
    }

    @PutMapping("/{id}")
    fun atualizar(@PathVariable id: Long, @Valid @RequestBody request: AlunoRequest): AlunoResponse =
        service.atualizar(id, request)

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun remover(@PathVariable id: Long) = service.remover(id)
}
